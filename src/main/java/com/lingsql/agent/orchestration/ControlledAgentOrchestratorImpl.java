package com.lingsql.agent.orchestration;

import com.lingsql.agent.collaboration.AgentRole;
import com.lingsql.agent.plugin.AgentPluginException;
import com.lingsql.agent.plugin.AgentPluginExecutor;
import com.lingsql.billing.invocation.PhysicalBillingException;
import com.lingsql.chat.model.ChatTraceStep;
import com.lingsql.common.exception.BizException;
import com.lingsql.common.exception.ErrorCode;
import com.lingsql.executor.ResultRefinement;
import com.lingsql.executor.ResultRefiner;
import com.lingsql.executor.SmartSqlExecutor;
import com.lingsql.executor.SqlExecutionResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 固定类型化状态机的受控 Agent 编排实现。
 *
 * <p>当前工作流固定为 SQL 架构师、强制 SQL 安全执行链、业务分析师。动态规划不能改变或绕过 SQL 安全顺序。</p>
 *
 * @author 余炘洋
 * @date 2026-08-15
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ControlledAgentOrchestratorImpl implements IControlledAgentOrchestrator {

    private static final String WORKFLOW_VERSION = "text-to-sql-v1";
    private static final String EXECUTION_ERROR_MESSAGE = "SQL 执行失败，请检查查询条件";
    private static final String TENANT_DB_CONFIG_USER_MESSAGE = "当前租户未完成数据源配置，请联系管理员补充数据库连接信息";
    private static final AgentRunPolicy DEFAULT_POLICY = new AgentRunPolicy(
            Set.of(SmartSqlExecutor.PLUGIN_ID),
            1,
            AgentCallerType.SYSTEM,
            3,
            3,
            120_000L
    );

    private final AgentPluginExecutor pluginExecutor;
    private final ResultRefiner resultRefiner;
    private final com.lingsql.agent.service.AgentCheckpointService checkpointService;

    @Override
    public AgentRunResult run(AgentRunCommand command) {
        validate(command);
        long start = System.currentTimeMillis();
        String executionId = UUID.randomUUID().toString();
        AgentExecutionContext executionContext = new AgentExecutionContext(
                executionId,
                command.requestId(),
                command.tenantId(),
                command.userId(),
                command.conversationId()
        );
        AgentRunControl runControl = new AgentRunControl(DEFAULT_POLICY);
        SqlExecutionResult executionResult;
        try {
            checkpointService.save(command.tenantId(), executionId, command.conversationId(),
                    "SQL_ARCHITECT", "DISPATCHED", null);
            executionResult = pluginExecutor.execute(
                    SmartSqlExecutor.PLUGIN_ID,
                    executionContext,
                    runControl,
                    new SqlQueryTask(command.question().trim(), command.requestSource()),
                    SqlExecutionResult.class
            );
        } catch (Exception exception) {
            log.error("Agent 工作流执行失败: executionId={}, requestId={}, tenantId={}, userId={}",
                    executionId, command.requestId(), command.tenantId(), command.userId(), exception);
            checkpointService.save(command.tenantId(), executionId, command.conversationId(),
                    "SECURE_QUERY", "FAILED", null);
            return buildFailedRun(
                    executionId,
                    buildFailureResult(exception),
                    resolveFailureCode(exception),
                    start
            );
        }
        SqlExecutionResult publicResult = sanitizeForUser(executionResult);
        if (publicResult == null || !publicResult.isSuccess()) {
            checkpointService.save(command.tenantId(), executionId, command.conversationId(),
                    "SECURE_QUERY", "FAILED", null);
            return buildFailedRun(executionId, publicResult, AgentFailureCode.SQL_EXECUTION_FAILED, start);
        }
        checkpointService.save(command.tenantId(), executionId, command.conversationId(),
                "SECURE_QUERY", "SUCCEEDED", null);

        ResultRefinement refinement;
        try {
            runControl.assertWithinDeadline();
            refinement = resultRefiner.refineWithMetadata(
                    executionContext,
                    command.question().trim(),
                    publicResult
            );
            runControl.assertWithinDeadline();
        } catch (Exception exception) {
            log.error("Agent 分析阶段执行失败: executionId={}, requestId={}, tenantId={}, userId={}",
                    executionId, command.requestId(), command.tenantId(), command.userId(), exception);
            return buildFailedRun(
                    executionId,
                    buildFailureResult(exception),
                    resolveFailureCode(exception),
                    start
            );
        }
        List<ChatTraceStep> trace = appendAnalystTrace(publicResult, refinement);
        AgentRunStatus status = refinement.success()
                ? AgentRunStatus.SUCCEEDED
                : AgentRunStatus.SUCCEEDED_WITH_DEGRADATION;
        checkpointService.save(command.tenantId(), executionId, command.conversationId(),
                "ANALYST", status.name(), null);
        return new AgentRunResult(
                executionId,
                WORKFLOW_VERSION,
                status,
                refinement.answer(),
                publicResult.getSql(),
                publicResult.getData(),
                trace,
                null,
                System.currentTimeMillis() - start
        );
    }

    private void validate(AgentRunCommand command) {
        if (command == null
                || !StringUtils.hasText(command.tenantId())
                || !StringUtils.hasText(command.question())
                || !StringUtils.hasText(command.requestSource())
                || !StringUtils.hasText(command.requestId())) {
            throw new BizException(ErrorCode.INVALID_PARAM, "Agent 运行上下文不完整");
        }
    }

    private AgentRunResult buildFailedRun(
            String executionId,
            SqlExecutionResult result,
            AgentFailureCode classifiedFailure,
            long start
    ) {
        String message = result != null && TENANT_DB_CONFIG_USER_MESSAGE.equals(result.getErrorMessage())
                ? TENANT_DB_CONFIG_USER_MESSAGE
                : EXECUTION_ERROR_MESSAGE;
        AgentFailureCode failureCode = TENANT_DB_CONFIG_USER_MESSAGE.equals(message)
                ? AgentFailureCode.TENANT_DATABASE_NOT_CONFIGURED
                : classifiedFailure;
        String sql = "ERROR:" + message;
        List<ChatTraceStep> trace = result == null ? buildFallbackTrace() : result.getTrace();
        return new AgentRunResult(
                executionId,
                WORKFLOW_VERSION,
                AgentRunStatus.FAILED,
                message,
                sql,
                List.of(),
                trace,
                failureCode,
                System.currentTimeMillis() - start
        );
    }

    private SqlExecutionResult sanitizeForUser(SqlExecutionResult executionResult) {
        if (executionResult == null || executionResult.isSuccess()) {
            return executionResult;
        }
        if (TENANT_DB_CONFIG_USER_MESSAGE.equals(executionResult.getErrorMessage())) {
            return executionResult;
        }
        return SqlExecutionResult.failure(executionResult.getSql(), EXECUTION_ERROR_MESSAGE, executionResult.getTrace());
    }

    private SqlExecutionResult buildFailureResult(Exception exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof BizException bizException
                    && bizException.getErrorCode() == ErrorCode.TENANT_DB_CONFIG_NOT_FOUND) {
                return SqlExecutionResult.failure(null, TENANT_DB_CONFIG_USER_MESSAGE, buildTenantConfigMissingTrace());
            }
            current = current.getCause();
        }
        return SqlExecutionResult.failure(null, EXECUTION_ERROR_MESSAGE, buildFallbackTrace());
    }

    private AgentFailureCode resolveFailureCode(Exception exception) {
        Throwable current = exception;
        AgentFailureCode pluginFailure = null;
        while (current != null) {
            if (current instanceof AgentBillingSettlementException
                    || current instanceof PhysicalBillingException) {
                return AgentFailureCode.BILLING_SETTLEMENT_FAILED;
            }
            if (current instanceof AgentAuditPersistenceException) {
                return AgentFailureCode.AUDIT_PERSIST_FAILED;
            }
            if (current instanceof BizException bizException) {
                AgentFailureCode securityFailure = switch (bizException.getErrorCode()) {
                    case SECURITY_WRITE_BLOCKED -> AgentFailureCode.SECURITY_WRITE_BLOCKED;
                    case SECURITY_TENANT_SCOPE_BLOCKED -> AgentFailureCode.SECURITY_TENANT_SCOPE_BLOCKED;
                    case SECURITY_ALLOWLIST_BLOCKED -> AgentFailureCode.SECURITY_ALLOWLIST_BLOCKED;
                    default -> null;
                };
                if (securityFailure != null) {
                    return securityFailure;
                }
            }
            if (current instanceof AgentPluginException pluginException) {
                AgentFailureCode classified = switch (pluginException.getReason()) {
                    case POLICY_DENIED -> AgentFailureCode.PLUGIN_POLICY_DENIED;
                    case BUDGET_EXHAUSTED -> AgentFailureCode.BUDGET_EXHAUSTED;
                    case DEADLINE_EXCEEDED -> AgentFailureCode.DEADLINE_EXCEEDED;
                    case CONTRACT_VIOLATION, PLUGIN_FAILED -> AgentFailureCode.PLUGIN_EXECUTION_FAILED;
                };
                if (pluginException.getReason() != com.lingsql.agent.plugin.AgentPluginFailureReason.PLUGIN_FAILED) {
                    return classified;
                }
                pluginFailure = classified;
            }
            current = current.getCause();
        }
        return pluginFailure == null ? AgentFailureCode.INTERNAL_ERROR : pluginFailure;
    }

    private List<ChatTraceStep> appendAnalystTrace(SqlExecutionResult result, ResultRefinement refinement) {
        List<ChatTraceStep> trace = new ArrayList<>();
        if (result.getTrace() != null) {
            trace.addAll(result.getTrace());
        }
        int attempt = resolveLastAttempt(trace);
        String status = refinement.success() ? "SUCCESS" : "FAILURE";
        String content = refinement.success()
                ? "业务分析师已基于通过审计的查询结果生成面向用户的答复。"
                : "业务分析师未能完成模型答复，系统已使用稳定降级答复。";
        trace.add(new ChatTraceStep(
                "FINAL",
                "业务分析师生成答复",
                content,
                status,
                attempt,
                AgentRole.ANALYST.name(),
                refinement.durationMs()
        ));
        return List.copyOf(trace);
    }

    private int resolveLastAttempt(List<ChatTraceStep> trace) {
        return trace.stream()
                .map(ChatTraceStep::getAttempt)
                .filter(attempt -> attempt != null && attempt > 0)
                .max(Integer::compareTo)
                .orElse(1);
    }

    private List<ChatTraceStep> buildFallbackTrace() {
        return List.of(
                new ChatTraceStep("THOUGHT", "接收用户问题", "系统已接收问题并准备启动 SQL 执行链路。", "SUCCESS", 1),
                new ChatTraceStep("OBSERVATION", "执行链路异常", "后端在启动执行链路时发生异常，已返回安全失败提示。", "FAILURE", 1),
                new ChatTraceStep("FINAL", "结束处理", "本次请求未能完成查询，请稍后重试或补充更明确的筛选条件。", "FAILURE", 1)
        );
    }

    private List<ChatTraceStep> buildTenantConfigMissingTrace() {
        return List.of(
                new ChatTraceStep("THOUGHT", "接收用户问题", "系统已接收问题并准备启动 SQL 执行链路。", "SUCCESS", 1),
                new ChatTraceStep("OBSERVATION", "租户数据源未配置", "当前租户尚未完成数据库连接配置，系统未进入 SQL 生成与执行流程。", "FAILURE", 1),
                new ChatTraceStep("FINAL", "结束处理", "请联系管理员补充当前租户的数据源配置后重试。", "FAILURE", 1)
        );
    }
}
