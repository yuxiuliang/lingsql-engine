package com.lingsql.executor;

import com.lingsql.agent.SqlAgentService;
import com.lingsql.agent.collaboration.AgentRole;
import com.lingsql.agent.orchestration.AgentAuditPersistenceException;
import com.lingsql.agent.orchestration.AgentBillingSettlementException;
import com.lingsql.agent.orchestration.AgentExecutionContext;
import com.lingsql.agent.orchestration.AgentRunControl;
import com.lingsql.agent.orchestration.SqlQueryTask;
import com.lingsql.agent.orchestration.sql.CandidateSql;
import com.lingsql.agent.orchestration.sql.GuardedQueryException;
import com.lingsql.agent.orchestration.sql.GuardedQueryEventSink;
import com.lingsql.agent.orchestration.sql.GuardedQueryResult;
import com.lingsql.agent.orchestration.sql.GuardedQueryStage;
import com.lingsql.agent.orchestration.sql.SqlGuardedQueryExecutor;
import com.lingsql.agent.plugin.AgentPluginAccess;
import com.lingsql.agent.plugin.AgentPluginCost;
import com.lingsql.agent.plugin.AgentPluginDescriptor;
import com.lingsql.agent.plugin.IAgentPlugin;
import com.lingsql.audit.model.SqlExecuteLogCommand;
import com.lingsql.audit.service.SqlExecuteLogService;
import com.lingsql.billing.TenantBillingService;
import com.lingsql.chat.model.ChatTraceStep;
import com.lingsql.common.trace.RequestTraceContext;
import com.lingsql.context.TenantContext;
import com.lingsql.datasource.TenantDbConfigErrors;
import com.lingsql.datasource.model.TenantDbConfig;
import com.lingsql.datasource.repository.TenantDbConfigRepository;
import com.lingsql.llm.LlmTraceContext;
import com.lingsql.llm.LlmTraceInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
public class SmartSqlExecutor implements IAgentPlugin<SqlQueryTask, SqlExecutionResult> {

    public static final String PLUGIN_ID = "secure_query";
    private static final AgentPluginDescriptor PLUGIN_DESCRIPTOR = new AgentPluginDescriptor(
            PLUGIN_ID,
            "1.0.0",
            AgentPluginAccess.SYSTEM_ONLY,
            AgentPluginCost.HIGH,
            false,
            "生成、审计并执行只读 SQL 查询"
    );

    private static final String TRACE_TYPE_THOUGHT = "THOUGHT";
    private static final String TRACE_TYPE_ACTION = "ACTION";
    private static final String TRACE_TYPE_OBSERVATION = "OBSERVATION";
    private static final String TRACE_TYPE_FINAL = "FINAL";
    private static final String TRACE_STATUS_SUCCESS = "SUCCESS";
    private static final String TRACE_STATUS_FAILURE = "FAILURE";

    private final SqlAgentService agentService;
    private final SqlGuardedQueryExecutor guardedQueryExecutor;
    private final SqlRecoveryPolicy recoveryPolicy;
    private final TenantDbConfigRepository tenantDbConfigRepository;
    private final SqlExecuteLogService sqlExecuteLogService;
    private final SqlTaskTracker sqlTaskTracker;
    private final TenantBillingService tenantBillingService;

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
        AgentExecutionContext context = new AgentExecutionContext(
                RequestTraceContext.resolveRequestId(),
                RequestTraceContext.resolveRequestId(),
                tenantId,
                TenantContext.getUserId(),
                TenantContext.getConversationId()
        );
        return execute(context, question, requestSource);
    }

    /**
     * 使用显式上下文执行自然语言查询。
     *
     * @param context Agent 执行上下文
     * @param question 用户问题
     * @param requestSource 请求来源
     * @return 执行结果
     * @author 余炘洋
     * @date 2026-08-15
     */
    public SqlExecutionResult execute(
            AgentExecutionContext context,
            String question,
            String requestSource
    ) {
        return executeInternal(context, question, requestSource, recoveryPolicy.resolveMaxAttempts());
    }

    @Override
    public AgentPluginDescriptor descriptor() {
        return PLUGIN_DESCRIPTOR;
    }

    @Override
    public Class<SqlQueryTask> inputType() {
        return SqlQueryTask.class;
    }

    @Override
    public Class<SqlExecutionResult> outputType() {
        return SqlExecutionResult.class;
    }

    @Override
    public SqlExecutionResult execute(
            AgentExecutionContext context,
            AgentRunControl control,
            SqlQueryTask input
    ) {
        control.assertWithinDeadline();
        int maxAttempts = Math.min(recoveryPolicy.resolveMaxAttempts(), control.maxSqlAttempts());
        return executeInternal(context, input.question(), input.requestSource(), maxAttempts);
    }

    private SqlExecutionResult executeInternal(
            AgentExecutionContext context,
            String question,
            String requestSource,
            int maxAttempts
    ) {
        String tenantId = context.tenantId();
        SqlExecuteLogCommand logCommand = new SqlExecuteLogCommand();
        List<ChatTraceStep> trace = new ArrayList<>();
        logCommand.setRequestId(context.requestId());
        logCommand.setTenantId(tenantId);
        logCommand.setUserId(context.userId());
        logCommand.setQuestion(question);
        logCommand.setRequestSource(requestSource);
        LlmTraceContext.clear();
        sqlTaskTracker.start(tenantId);
        appendTrace(trace, TRACE_TYPE_ACTION, "SQL 架构师接收问题", "已接收查询问题，将基于租户上下文、语义字典和历史会话生成 SQL 方案。",
                TRACE_STATUS_SUCCESS, 1, AgentRole.ARCHITECT, null);
        RuntimeException primaryFailure = null;
        try {
            TenantDbConfig config = tenantDbConfigRepository.findByTenantId(tenantId)
                    .orElseThrow(TenantDbConfigErrors::missingConfig);
            return executeWithRetry(config, tenantId, question, logCommand, trace, maxAttempts);
        } catch (RuntimeException ex) {
            primaryFailure = ex;
            fillFailureLog(logCommand, null, ex.getMessage());
            appendTrace(trace, TRACE_TYPE_OBSERVATION, "执行链路中断", "系统在准备执行环境时发生异常，未能进入 SQL 生成流程。",
                    TRACE_STATUS_FAILURE, 1, AgentRole.SYSTEM, null);
            throw ex;
        } finally {
            try {
                try {
                    finalizeBillingAndAudit(tenantId, logCommand);
                } catch (RuntimeException finalizationFailure) {
                    if (primaryFailure == null) {
                        throw finalizationFailure;
                    }
                    primaryFailure.addSuppressed(finalizationFailure);
                }
            } finally {
                LlmTraceContext.clear();
                sqlTaskTracker.finish(tenantId);
            }
        }
    }

    /**
     * 执行 SQL，并在受控错误类型下执行有限轮次的自愈。
     *
     * @param config     租户配置
     * @param tenantId   租户标识
     * @param question   用户问题
     * @param logCommand 日志对象
     * @param trace      执行轨迹
     * @return 执行结果
     * @author 余炘洋
     * @date 2026-07-29
     */
    private SqlExecutionResult executeWithRetry(
            TenantDbConfig config,
            String tenantId,
            String question,
            SqlExecuteLogCommand logCommand,
            List<ChatTraceStep> trace,
            int maxAttempts
    ) {
        String recoveryObservation = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return executeOnce(config, tenantId, question, recoveryObservation, logCommand, trace, attempt);
            } catch (SqlExecutionException exception) {
                SqlRecoveryDecision decision = recoveryPolicy.assess(exception);
                if (!decision.recoverable() || attempt >= maxAttempts) {
                    log.warn("SQL 执行结束: attempt={}, recoverable={}, reason={}", attempt, decision.recoverable(), exception.getMessage());
                    fillFailureLog(logCommand, exception.getSql(), exception.getMessage());
                    appendTrace(trace, TRACE_TYPE_FINAL, "结束处理", resolveTerminalFailureContent(attempt, maxAttempts, decision),
                            TRACE_STATUS_FAILURE, attempt, AgentRole.SYSTEM, null);
                    return SqlExecutionResult.failure(exception.getSql(), exception.getMessage(), trace);
                }
                appendTrace(trace, TRACE_TYPE_OBSERVATION, resolveFailureTitle(attempt), resolveRecoveryContent(decision),
                        TRACE_STATUS_FAILURE, attempt, AgentRole.SYSTEM, null);
                if (decision.refreshSchema() && !refreshSchemaForRecovery(tenantId, logCommand, trace, attempt, exception)) {
                    return SqlExecutionResult.failure(exception.getSql(), exception.getMessage(), trace);
                }
                int nextAttempt = attempt + 1;
                appendTrace(trace, TRACE_TYPE_ACTION, "准备第 " + nextAttempt + " 轮自愈", "已记录受控观察结果，准备重新生成候选 SQL。",
                        TRACE_STATUS_SUCCESS, nextAttempt, AgentRole.ARCHITECT, null);
                recoveryObservation = decision.observation();
            }
        }
        throw new IllegalStateException("SQL 自愈循环未返回结果");
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
     * @date 2026-07-29
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
        long generationStart = System.currentTimeMillis();
        String rawSql = reason == null
                ? agentService.generateSql(tenantId, question)
                : agentService.generateSqlWithRetry(tenantId, question, reason);
        long generationCostMs = System.currentTimeMillis() - generationStart;

        if (rawSql == null || rawSql.isBlank() || rawSql.toUpperCase().startsWith("ERROR")) {
            String failureMessage = "ERROR:NO_SCHEMA_MATCH".equalsIgnoreCase(rawSql)
                    ? "未匹配到可用表结构"
                    : "模型未能生成可执行 SQL，请补充查询条件";
            fillFailureLog(logCommand, rawSql, failureMessage);
            appendTrace(trace, TRACE_TYPE_OBSERVATION, resolveCandidateTitle(attempt), "SQL 架构师未返回可执行 SQL，本轮执行已中止。",
                    TRACE_STATUS_FAILURE, attempt, AgentRole.ARCHITECT, generationCostMs);
            throw new SqlExecutionException(failureMessage, rawSql);
        }
        logCommand.setAiSql(rawSql);
        applyModelTrace(logCommand);
        tenantBillingService.reserveCost(tenantId, logCommand);
        appendTrace(trace, TRACE_TYPE_OBSERVATION, resolveCandidateTitle(attempt), "SQL 架构师已生成候选 SQL，准备交由合规审计员校验。",
                TRACE_STATUS_SUCCESS, attempt, AgentRole.ARCHITECT, generationCostMs);

        GuardedQueryResult queryResult;
        try {
            queryResult = guardedQueryExecutor.execute(
                    new CandidateSql(rawSql),
                    config,
                    tenantId,
                    new GuardedQueryEventSink() {
                        @Override
                        public void onAuditApproved(String approvedSql, long auditDurationMs) {
                            appendTrace(trace, TRACE_TYPE_OBSERVATION, "合规审计通过",
                                    "合规审计员已完成租户隔离改写与安全校验，允许进入数据库执行。",
                                    TRACE_STATUS_SUCCESS, attempt, AgentRole.AUDITOR, auditDurationMs);
                        }

                        @Override
                        public void onQueryStarted(String approvedSql) {
                            appendTrace(trace, TRACE_TYPE_ACTION, resolveExecuteTitle(attempt),
                                    "已通过合规审计，正在执行受控 SQL 查询。",
                                    TRACE_STATUS_SUCCESS, attempt, AgentRole.SYSTEM, null);
                        }
                    }
            );
            List<Map<String, Object>> data = queryResult.data();
            logCommand.setExecutedSql(queryResult.approvedSql());
            logCommand.setExecuteCostMs(queryResult.queryDurationMs());
            logCommand.setResultRows(data == null ? 0 : data.size());
            logCommand.setSuccess(true);
            logCommand.setErrorMessage(null);
            appendTrace(trace, TRACE_TYPE_OBSERVATION, resolveResultTitle(attempt), "查询成功，返回 " + resolveRowCount(data) + " 行数据。",
                    TRACE_STATUS_SUCCESS, attempt, AgentRole.SYSTEM, queryResult.queryDurationMs());
            appendTrace(trace, TRACE_TYPE_FINAL, "SQL 执行完成", resolveFinalContent(attempt, data),
                    TRACE_STATUS_SUCCESS, attempt, AgentRole.SYSTEM, null);
            return SqlExecutionResult.success(queryResult.approvedSql(), data, trace);
        } catch (GuardedQueryException exception) {
            if (exception.getStage() == GuardedQueryStage.AUDIT) {
                fillFailureLog(logCommand, rawSql, exception.getMessage());
                appendTrace(trace, TRACE_TYPE_OBSERVATION, "合规审计拒绝", "合规审计员拒绝该候选 SQL，系统不会执行数据库查询。",
                        TRACE_STATUS_FAILURE, attempt, AgentRole.AUDITOR, exception.getAuditDurationMs());
                throw new SqlExecutionException(exception.getMessage(), rawSql);
            }
            fillFailureLog(logCommand, exception.getSql(), exception.getMessage());
            appendTrace(trace, TRACE_TYPE_OBSERVATION, resolveResultTitle(attempt), resolveFailureContent(attempt),
                    TRACE_STATUS_FAILURE, attempt, AgentRole.SYSTEM, null);
            throw new SqlExecutionException(exception.getMessage(), exception.getSql());
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
     * 结束执行后处理计费与审计日志。
     *
     * @param tenantId   租户标识
     * @param logCommand 日志对象
     * @author 余炘洋
     * @date 2026-02-26
     */
    private void finalizeBillingAndAudit(String tenantId, SqlExecuteLogCommand logCommand) {
        RuntimeException settlementFailure = null;
        try {
            tenantBillingService.charge(tenantId, logCommand);
        } catch (Exception ex) {
            log.error("执行后计费失败: tenantId={}, requestId={}", tenantId,
                    logCommand == null ? null : logCommand.getRequestId(), ex);
            settlementFailure = new AgentBillingSettlementException("执行后计费失败", ex);
        }
        try {
            sqlExecuteLogService.record(logCommand);
        } catch (Exception ex) {
            log.error("执行日志写入失败: tenantId={}, requestId={}", tenantId,
                    logCommand == null ? null : logCommand.getRequestId(), ex);
            if (settlementFailure == null) {
                settlementFailure = new AgentAuditPersistenceException("执行日志写入失败", ex);
            } else {
                settlementFailure.addSuppressed(new AgentAuditPersistenceException("执行日志写入失败", ex));
            }
        }
        if (settlementFailure != null) {
            throw settlementFailure;
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
     * @param role 角色
     * @param durationMs 处理耗时
     * @author 余炘洋
     * @date 2026-07-29
     */
    private void appendTrace(
            List<ChatTraceStep> trace,
            String type,
            String title,
            String content,
            String status,
            int attempt,
            AgentRole role,
            Long durationMs
    ) {
        if (trace == null) {
            return;
        }
        trace.add(new ChatTraceStep(type, title, content, status, attempt,
                role == null ? null : role.name(), durationMs));
    }

    /**
     * 刷新元数据并记录恢复结果。
     *
     * @param tenantId 租户标识
     * @param logCommand 审计日志命令
     * @param trace 轨迹
     * @param attempt 轮次
     * @param originalException 原始异常
     * @return 是否刷新成功
     * @author 余炘洋
     * @date 2026-07-29
     */
    private boolean refreshSchemaForRecovery(
            String tenantId,
            SqlExecuteLogCommand logCommand,
            List<ChatTraceStep> trace,
            int attempt,
            SqlExecutionException originalException
    ) {
        long refreshStart = System.currentTimeMillis();
        try {
            agentService.refreshSchema(tenantId);
            appendTrace(trace, TRACE_TYPE_OBSERVATION, "元数据刷新完成", "已重新读取允许访问的表结构，下一轮将使用最新元数据生成 SQL。",
                    TRACE_STATUS_SUCCESS, attempt, AgentRole.ARCHITECT, System.currentTimeMillis() - refreshStart);
            return true;
        } catch (Exception exception) {
            log.warn("SQL 自愈元数据刷新失败: tenantId={}, attempt={}", tenantId, attempt, exception);
            fillFailureLog(logCommand, originalException.getSql(), exception.getMessage());
            appendTrace(trace, TRACE_TYPE_OBSERVATION, "元数据刷新失败", "无法安全刷新当前租户元数据，系统已停止自动纠错。",
                    TRACE_STATUS_FAILURE, attempt, AgentRole.ARCHITECT, System.currentTimeMillis() - refreshStart);
            appendTrace(trace, TRACE_TYPE_FINAL, "结束处理", "元数据刷新未完成，系统已返回安全失败提示。",
                    TRACE_STATUS_FAILURE, attempt, AgentRole.SYSTEM, null);
            return false;
        }
    }

    /**
     * 构建可公开显示的恢复阶段标题。
     *
     * @param attempt 轮次
     * @return 标题
     * @author 余炘洋
     * @date 2026-07-29
     */
    private String resolveFailureTitle(int attempt) {
        return "第 " + attempt + " 轮执行失败";
    }

    /**
     * 获取候选 SQL 标题。
     *
     * @param attempt 轮次
     * @return 标题
     * @author 余炘洋
     * @date 2026-07-29
     */
    private String resolveCandidateTitle(int attempt) {
        return attempt == 1 ? "首轮候选 SQL" : "第 " + attempt + " 轮候选 SQL";
    }

    /**
     * 获取执行阶段标题。
     *
     * @param attempt 轮次
     * @return 标题
     * @author 余炘洋
     * @date 2026-07-29
     */
    private String resolveExecuteTitle(int attempt) {
        return attempt == 1 ? "执行首轮 SQL" : "执行第 " + attempt + " 轮 SQL";
    }

    /**
     * 获取结果阶段标题。
     *
     * @param attempt 轮次
     * @return 标题
     * @author 余炘洋
     * @date 2026-07-29
     */
    private String resolveResultTitle(int attempt) {
        return attempt == 1 ? "首轮执行结果" : "第 " + attempt + " 轮执行结果";
    }

    /**
     * 构建下一轮恢复提示。
     *
     * @param decision 恢复决策
     * @return 说明
     * @author 余炘洋
     * @date 2026-07-29
     */
    private String resolveRecoveryContent(SqlRecoveryDecision decision) {
        if (decision.refreshSchema()) {
            return "已识别到表结构失配，将先刷新元数据后进行下一轮 SQL 生成。";
        }
        return "已识别到可恢复的 SQL 语法问题，将使用受控观察结果重新生成 SQL。";
    }

    /**
     * 获取当前轮次失败的公开说明。
     *
     * @param attempt 轮次
     * @return 说明
     * @author 余炘洋
     * @date 2026-07-29
     */
    private String resolveFailureContent(int attempt) {
        return "第 " + attempt + " 轮 SQL 未成功执行，系统将根据恢复策略决定是否继续处理。";
    }

    /**
     * 构建终止处理说明。
     *
     * @param attempt 当前轮次
     * @param maxAttempts 最大轮次
     * @param decision 恢复决策
     * @return 说明
     * @author 余炘洋
     * @date 2026-07-29
     */
    private String resolveTerminalFailureContent(int attempt, int maxAttempts, SqlRecoveryDecision decision) {
        if (!decision.recoverable()) {
            return "当前失败类型不允许自动重试，系统已返回安全失败提示。";
        }
        return "已达到最多 " + maxAttempts + " 轮执行上限，第 " + attempt + " 轮仍未成功，系统已返回安全失败提示。";
    }

    /**
     * 获取最终说明。
     *
     * @param attempt 轮次
     * @param data    数据结果
     * @return 说明
     * @author 余炘洋
     * @date 2026-07-29
     */
    private String resolveFinalContent(int attempt, List<Map<String, Object>> data) {
        String resultSummary = "本次查询共返回 " + resolveRowCount(data) + " 行结果。";
        if (attempt > 1) {
            return "系统已完成第 " + attempt + " 轮自愈并返回结果。" + resultSummary;
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
