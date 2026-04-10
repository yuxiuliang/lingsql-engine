package com.lingsql.executor;

import com.lingsql.agent.SqlAgentService;
import com.lingsql.audit.model.SqlExecuteLogCommand;
import com.lingsql.audit.service.SqlExecuteLogService;
import com.lingsql.billing.TenantBillingService;
import com.lingsql.chat.model.ChatTraceStep;
import com.lingsql.common.exception.BizException;
import com.lingsql.common.exception.ErrorCode;
import com.lingsql.common.trace.RequestTraceContext;
import com.lingsql.context.TenantContext;
import com.lingsql.datasource.TenantDbConfigErrors;
import com.lingsql.datasource.model.TenantDbConfig;
import com.lingsql.datasource.repository.TenantDbConfigRepository;
import com.lingsql.llm.LlmTraceContext;
import com.lingsql.llm.LlmTraceInfo;
import com.lingsql.security.SqlRewriter;
import com.lingsql.security.SqlSecurityChecker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 智能 SQL 执行器：SQL 生成、改写、校验与执行。
 *
 * @author 余炘洋
 * @date 2026-01-22
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SmartSqlExecutor {

    private static final String TRACE_TYPE_THOUGHT = "THOUGHT";
    private static final String TRACE_TYPE_ACTION = "ACTION";
    private static final String TRACE_TYPE_OBSERVATION = "OBSERVATION";
    private static final String TRACE_TYPE_FINAL = "FINAL";
    private static final String TRACE_STATUS_SUCCESS = "SUCCESS";
    private static final String TRACE_STATUS_FAILURE = "FAILURE";

    private final SqlAgentService agentService;
    private final SqlSecurityChecker securityChecker;
    private final SqlRewriter rewriter;
    private final TenantDbConfigRepository tenantDbConfigRepository;
    private final SqlExecuteLogService sqlExecuteLogService;
    private final SqlTaskTracker sqlTaskTracker;
    private final TenantBillingService tenantBillingService;

    @Qualifier("businessJdbcTemplate")
    private final JdbcTemplate jdbcTemplate;

    /**
     * 执行自然语言查询。
     *
     * @param tenantId 租户标识
     * @param question 用户问题
     * @return 执行结果
     * @author 余炘洋
     * @date 2026-01-22
     */
    public SqlExecutionResult execute(String tenantId, String question) {
        return execute(tenantId, question, "UNKNOWN");
    }

    /**
     * 执行自然语言查询（带来源标识）。
     *
     * @param tenantId      租户标识
     * @param question      用户问题
     * @param requestSource 请求来源
     * @return 执行结果
     * @author 余炘洋
     * @date 2026-01-26
     */
    public SqlExecutionResult execute(String tenantId, String question, String requestSource) {
        SqlExecuteLogCommand logCommand = new SqlExecuteLogCommand();
        List<ChatTraceStep> trace = new ArrayList<>();
        logCommand.setRequestId(RequestTraceContext.resolveRequestId());
        logCommand.setTenantId(tenantId);
        logCommand.setUserId(TenantContext.getUserId());
        logCommand.setQuestion(question);
        logCommand.setRequestSource(requestSource);
        LlmTraceContext.clear();
        sqlTaskTracker.start(tenantId);
        appendTrace(trace, TRACE_TYPE_THOUGHT, "解析用户问题", "已结合租户上下文、语义字典和历史会话分析本次查询意图。", TRACE_STATUS_SUCCESS, 1);
        try {
            TenantDbConfig config = tenantDbConfigRepository.findByTenantId(tenantId)
                    .orElseThrow(TenantDbConfigErrors::missingConfig);
            return executeWithRetry(config, tenantId, question, logCommand, trace);
        } catch (Exception ex) {
            fillFailureLog(logCommand, null, ex.getMessage());
            appendTrace(trace, TRACE_TYPE_OBSERVATION, "执行链路中断", "系统在准备执行环境时发生异常，未能进入 SQL 生成流程。", TRACE_STATUS_FAILURE, 1);
            throw ex;
        } finally {
            finalizeBillingAndAudit(tenantId, logCommand);
            LlmTraceContext.clear();
            sqlTaskTracker.finish(tenantId);
        }
    }

    /**
     * 执行并带一次重试逻辑。
     *
     * @param config     租户配置
     * @param tenantId   租户标识
     * @param question   用户问题
     * @param logCommand 日志对象
     * @param trace      执行轨迹
     * @return 执行结果
     * @author 余炘洋
     * @date 2026-04-07
     */
    private SqlExecutionResult executeWithRetry(
            TenantDbConfig config,
            String tenantId,
            String question,
            SqlExecuteLogCommand logCommand,
            List<ChatTraceStep> trace
    ) {
        try {
            return executeOnce(config, tenantId, question, null, logCommand, trace, 1);
        } catch (SqlExecutionException ex) {
            log.warn("首次执行失败，准备重试: {}", ex.getMessage());
            appendTrace(trace, TRACE_TYPE_OBSERVATION, "首轮执行失败", "系统已捕获首轮错误，准备自动执行一次自愈纠错。", TRACE_STATUS_FAILURE, 1);
            appendTrace(trace, TRACE_TYPE_THOUGHT, "进入自愈纠错", "已回注失败原因，并重新检查元数据与租户隔离规则。", TRACE_STATUS_SUCCESS, 2);
            try {
                return executeOnce(config, tenantId, question, ex.getMessage(), logCommand, trace, 2);
            } catch (SqlExecutionException retryEx) {
                log.error("重试失败: {}", retryEx.getMessage());
                fillFailureLog(logCommand, retryEx.getSql(), retryEx.getMessage());
                appendTrace(trace, TRACE_TYPE_FINAL, "结束处理", "第二轮执行仍未成功，系统已返回安全失败提示。", TRACE_STATUS_FAILURE, 2);
                return SqlExecutionResult.failure(retryEx.getSql(), retryEx.getMessage(), trace);
            }
        }
    }

    /**
     * 单次执行流程。
     *
     * @param config     租户配置
     * @param tenantId   租户标识
     * @param question   用户问题
     * @param reason     失败原因
     * @param logCommand 日志对象
     * @param trace      执行轨迹
     * @param attempt    执行轮次
     * @return 执行结果
     * @author 余炘洋
     * @date 2026-04-07
     */
    private SqlExecutionResult executeOnce(
            TenantDbConfig config,
            String tenantId,
            String question,
            String reason,
            SqlExecuteLogCommand logCommand,
            List<ChatTraceStep> trace,
            int attempt
    ) {
        LlmTraceContext.clear();
        appendTrace(trace, TRACE_TYPE_ACTION, resolveGenerateTitle(attempt), resolveGenerateContent(attempt), TRACE_STATUS_SUCCESS, attempt);
        String rawSql = reason == null
                ? agentService.generateSql(tenantId, question)
                : agentService.generateSqlWithRetry(tenantId, question, reason);

        if (rawSql == null || rawSql.isBlank() || rawSql.toUpperCase().startsWith("ERROR")) {
            fillFailureLog(logCommand, rawSql, "模型未能生成可执行 SQL，请补充查询条件");
            appendTrace(trace, TRACE_TYPE_OBSERVATION, resolveCandidateTitle(attempt), "模型未返回可执行 SQL，本轮执行已中止。", TRACE_STATUS_FAILURE, attempt);
            throw new SqlExecutionException("模型未能生成可执行 SQL，请补充查询条件", rawSql);
        }
        logCommand.setAiSql(rawSql);
        applyModelTrace(logCommand);
        tenantBillingService.reserveCost(tenantId, logCommand);
        appendTrace(trace, TRACE_TYPE_OBSERVATION, resolveCandidateTitle(attempt), "已生成候选 SQL，准备执行隔离改写与安全审计。", TRACE_STATUS_SUCCESS, attempt);

        String rewrittenSql = rewriter.rewrite(rawSql, config, tenantId);
        try {
            securityChecker.check(rewrittenSql, config, tenantId);
            appendTrace(trace, TRACE_TYPE_ACTION, resolveExecuteTitle(attempt), "已完成租户隔离改写并进入数据库执行。", TRACE_STATUS_SUCCESS, attempt);
            long start = System.currentTimeMillis();
            List<Map<String, Object>> data = jdbcTemplate.queryForList(rewrittenSql);
            long costMs = System.currentTimeMillis() - start;
            logCommand.setExecutedSql(rewrittenSql);
            logCommand.setExecuteCostMs(costMs);
            logCommand.setResultRows(data == null ? 0 : data.size());
            logCommand.setSuccess(true);
            logCommand.setErrorMessage(null);
            appendTrace(trace, TRACE_TYPE_OBSERVATION, resolveResultTitle(attempt), "查询成功，返回 " + resolveRowCount(data) + " 行数据。", TRACE_STATUS_SUCCESS, attempt);
            appendTrace(trace, TRACE_TYPE_FINAL, "完成答复", resolveFinalContent(attempt, data), TRACE_STATUS_SUCCESS, attempt);
            return SqlExecutionResult.success(rewrittenSql, data, trace);
        } catch (Exception ex) {
            fillFailureLog(logCommand, rewrittenSql, ex.getMessage());
            appendTrace(trace, TRACE_TYPE_OBSERVATION, resolveResultTitle(attempt), resolveFailureContent(attempt), TRACE_STATUS_FAILURE, attempt);
            throw new SqlExecutionException(ex.getMessage(), rewrittenSql);
        }
    }

    /**
     * 填充失败日志信息。
     *
     * @param logCommand  日志对象
     * @param sql         SQL 文本
     * @param errorReason 错误信息
     * @author 余炘洋
     * @date 2026-01-26
     */
    private void fillFailureLog(SqlExecuteLogCommand logCommand, String sql, String errorReason) {
        if (logCommand == null) {
            return;
        }
        if (logCommand.getAiSql() == null) {
            logCommand.setAiSql(sql);
        } else {
            logCommand.setExecutedSql(sql);
        }
        logCommand.setSuccess(false);
        logCommand.setErrorMessage(errorReason);
        applyModelTrace(logCommand);
    }

    /**
     * 应用模型追踪信息。
     *
     * @param logCommand 日志对象
     * @author 余炘洋
     * @date 2026-01-26
     */
    private void applyModelTrace(SqlExecuteLogCommand logCommand) {
        if (logCommand == null) {
            return;
        }
        LlmTraceInfo traceInfo = LlmTraceContext.get();
        if (traceInfo == null) {
            return;
        }
        logCommand.setModelProvider(traceInfo.getProviderName());
        logCommand.setModelName(traceInfo.getModelName());
        logCommand.setModelVersion(traceInfo.getModelVersion());
        Integer tokens = traceInfo.getTokenCount();
        if (tokens == null || tokens <= 0) {
            return;
        }
        logCommand.addModelTokens(traceInfo.getModelName(), tokens);
        Integer current = logCommand.getTokenCount();
        if (current == null) {
            logCommand.setTokenCount(tokens);
            return;
        }
        long total = (long) current + tokens;
        logCommand.setTokenCount(total > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) total);
    }

    /**
     * 结束执行后处理计费与审计日志，避免非核心流程异常反向影响主链路。
     *
     * @param tenantId   租户标识
     * @param logCommand 日志对象
     * @author 余炘洋
     * @date 2026-02-26
     */
    private void finalizeBillingAndAudit(String tenantId, SqlExecuteLogCommand logCommand) {
        try {
            tenantBillingService.charge(tenantId, logCommand);
        } catch (Exception ex) {
            log.error("执行后计费失败: tenantId={}, requestId={}", tenantId,
                    logCommand == null ? null : logCommand.getRequestId(), ex);
        }
        try {
            sqlExecuteLogService.record(logCommand);
        } catch (Exception ex) {
            log.error("执行日志写入失败: tenantId={}, requestId={}", tenantId,
                    logCommand == null ? null : logCommand.getRequestId(), ex);
        }
    }

    /**
     * 追加轨迹步骤。
     *
     * @param trace   轨迹集合
     * @param type    类型
     * @param title   标题
     * @param content 内容
     * @param status  状态
     * @param attempt 轮次
     * @author 余炘洋
     * @date 2026-04-07
     */
    private void appendTrace(
            List<ChatTraceStep> trace,
            String type,
            String title,
            String content,
            String status,
            int attempt
    ) {
        if (trace == null) {
            return;
        }
        trace.add(new ChatTraceStep(type, title, content, status, attempt));
    }

    /**
     * 获取生成阶段标题。
     *
     * @param attempt 轮次
     * @return 标题
     * @author 余炘洋
     * @date 2026-04-07
     */
    private String resolveGenerateTitle(int attempt) {
        return attempt == 1 ? "生成 SQL 方案" : "重新生成 SQL 方案";
    }

    /**
     * 获取生成阶段说明。
     *
     * @param attempt 轮次
     * @return 说明
     * @author 余炘洋
     * @date 2026-04-07
     */
    private String resolveGenerateContent(int attempt) {
        return attempt == 1
                ? "正在基于真实元数据、语义字典与 Join 规则生成首轮 SQL。"
                : "正在携带失败原因重新检查元数据，并生成新的 SQL 方案。";
    }

    /**
     * 获取候选 SQL 标题。
     *
     * @param attempt 轮次
     * @return 标题
     * @author 余炘洋
     * @date 2026-04-07
     */
    private String resolveCandidateTitle(int attempt) {
        return attempt == 1 ? "首轮候选 SQL" : "纠错候选 SQL";
    }

    /**
     * 获取执行阶段标题。
     *
     * @param attempt 轮次
     * @return 标题
     * @author 余炘洋
     * @date 2026-04-07
     */
    private String resolveExecuteTitle(int attempt) {
        return attempt == 1 ? "执行首轮 SQL" : "执行纠错 SQL";
    }

    /**
     * 获取结果阶段标题。
     *
     * @param attempt 轮次
     * @return 标题
     * @author 余炘洋
     * @date 2026-04-07
     */
    private String resolveResultTitle(int attempt) {
        return attempt == 1 ? "首轮执行结果" : "纠错执行结果";
    }

    /**
     * 获取失败说明。
     *
     * @param attempt 轮次
     * @return 说明
     * @author 余炘洋
     * @date 2026-04-07
     */
    private String resolveFailureContent(int attempt) {
        return attempt == 1
                ? "首轮 SQL 未通过执行校验，系统将自动尝试一次自愈纠错。"
                : "纠错 SQL 仍未成功执行，系统将返回安全失败提示。";
    }

    /**
     * 获取最终说明。
     *
     * @param attempt 轮次
     * @param data    数据结果
     * @return 说明
     * @author 余炘洋
     * @date 2026-04-07
     */
    private String resolveFinalContent(int attempt, List<Map<String, Object>> data) {
        String resultSummary = "本次查询共返回 " + resolveRowCount(data) + " 行结果。";
        if (attempt > 1) {
            return "系统已完成一次自愈纠错并返回结果。" + resultSummary;
        }
        return "查询链路执行完成，已返回最终结果。" + resultSummary;
    }

    /**
     * 统计结果行数。
     *
     * @param data 数据结果
     * @return 行数
     * @author 余炘洋
     * @date 2026-04-07
     */
    private int resolveRowCount(List<Map<String, Object>> data) {
        return data == null ? 0 : data.size();
    }
}
