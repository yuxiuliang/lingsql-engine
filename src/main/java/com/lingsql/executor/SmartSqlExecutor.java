package com.lingsql.executor;

import com.lingsql.agent.SqlAgentService;
import com.lingsql.audit.model.SqlExecuteLogCommand;
import com.lingsql.audit.service.SqlExecuteLogService;
import com.lingsql.billing.TenantBillingService;
import com.lingsql.common.exception.BizException;
import com.lingsql.common.exception.ErrorCode;
import com.lingsql.common.trace.RequestTraceContext;
import com.lingsql.context.TenantContext;
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
        logCommand.setRequestId(RequestTraceContext.resolveRequestId());
        logCommand.setTenantId(tenantId);
        logCommand.setUserId(TenantContext.getUserId());
        logCommand.setQuestion(question);
        logCommand.setRequestSource(requestSource);
        LlmTraceContext.clear();
        sqlTaskTracker.start(tenantId);
        try {
            TenantDbConfig config = tenantDbConfigRepository.findByTenantId(tenantId)
                    .orElseThrow(() -> new BizException(ErrorCode.TENANT_NOT_FOUND, "租户不存在"));
            return executeWithRetry(config, tenantId, question, logCommand);
        } catch (Exception ex) {
            fillFailureLog(logCommand, null, ex.getMessage());
            throw ex;
        } finally {
            tenantBillingService.charge(tenantId, logCommand);
            sqlExecuteLogService.record(logCommand);
            LlmTraceContext.clear();
            sqlTaskTracker.finish(tenantId);
        }
    }

    /**
     * 执行并带一次重试逻辑。
     *
     * @param config   租户配置
     * @param tenantId 租户标识
     * @param question 用户问题
     * @return 执行结果
     * @author 余炘洋
     * @date 2026-01-22
     */
    private SqlExecutionResult executeWithRetry(TenantDbConfig config, String tenantId, String question, SqlExecuteLogCommand logCommand) {
        try {
            return executeOnce(config, tenantId, question, null, logCommand);
        } catch (SqlExecutionException ex) {
            log.warn("首次执行失败，准备重试: {}", ex.getMessage());
            try {
                return executeOnce(config, tenantId, question, ex.getMessage(), logCommand);
            } catch (SqlExecutionException retryEx) {
                log.error("重试失败: {}", retryEx.getMessage());
                fillFailureLog(logCommand, retryEx.getSql(), retryEx.getMessage());
                return SqlExecutionResult.failure(retryEx.getSql(), retryEx.getMessage());
            }
        }
    }

    /**
     * 单次执行流程。
     *
     * @param config   租户配置
     * @param tenantId 租户标识
     * @param question 用户问题
     * @param reason   失败原因
     * @return 执行结果
     * @author 余炘洋
     * @date 2026-01-22
     */
    private SqlExecutionResult executeOnce(TenantDbConfig config, String tenantId, String question, String reason, SqlExecuteLogCommand logCommand) {
        LlmTraceContext.clear();
        String rawSql = reason == null
                ? agentService.generateSql(tenantId, question)
                : agentService.generateSqlWithRetry(tenantId, question, reason);

        if (rawSql == null || rawSql.isBlank() || rawSql.toUpperCase().startsWith("ERROR")) {
            fillFailureLog(logCommand, rawSql, "模型未能生成可执行 SQL，请补充查询条件");
            throw new SqlExecutionException("模型未能生成可执行 SQL，请补充查询条件", rawSql);
        }
        logCommand.setAiSql(rawSql);
        applyModelTrace(logCommand);
        tenantBillingService.reserveCost(tenantId, logCommand);

        String rewrittenSql = rewriter.rewrite(rawSql, config, tenantId);
        try {
            securityChecker.check(rewrittenSql, config);
            long start = System.currentTimeMillis();
            List<Map<String, Object>> data = jdbcTemplate.queryForList(rewrittenSql);
            long costMs = System.currentTimeMillis() - start;
            logCommand.setExecutedSql(rewrittenSql);
            logCommand.setExecuteCostMs(costMs);
            logCommand.setResultRows(data == null ? 0 : data.size());
            logCommand.setSuccess(true);
            logCommand.setErrorMessage(null);
            return SqlExecutionResult.success(rewrittenSql, data);
        } catch (Exception ex) {
            fillFailureLog(logCommand, rewrittenSql, ex.getMessage());
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
}
