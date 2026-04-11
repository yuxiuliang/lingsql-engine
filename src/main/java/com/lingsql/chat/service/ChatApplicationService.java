package com.lingsql.chat.service;

import com.lingsql.chat.entity.ChatHistory;
import com.lingsql.chat.model.ChatRequest;
import com.lingsql.chat.model.ChatResult;
import com.lingsql.chat.model.ChatTraceStep;
import com.lingsql.common.exception.BizException;
import com.lingsql.common.exception.ErrorCode;
import com.lingsql.context.TenantContext;
import com.lingsql.executor.ResultRefiner;
import com.lingsql.executor.SmartSqlExecutor;
import com.lingsql.executor.SqlExecutionResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.UUID;

/**
 * 问答业务编排服务。
 *
 * @author 余炘洋
 * @date 2026-01-28
 */
@Service
@RequiredArgsConstructor
public class ChatApplicationService {

    private static final String EXECUTION_ERROR_MESSAGE = "SQL 执行失败，请检查查询条件";
    private static final String TENANT_DB_CONFIG_USER_MESSAGE = "当前租户未完成数据源配置，请联系管理员补充数据库连接信息";
    private static final String QUESTION_REQUIRED_MESSAGE = "问题不能为空";

    private final SmartSqlExecutor sqlExecutor;
    private final ResultRefiner resultRefiner;
    private final ChatHistoryService chatHistoryService;
    private final ChatMemoryService chatMemoryService;

    /**
     * 处理问答请求。
     *
     * @param request 请求体
     * @return 问答结果
     * @author 余炘洋
     * @date 2026-01-28
     */
    public ChatResult ask(ChatRequest request) {
        if (request == null || !StringUtils.hasText(request.getQuestion())) {
            throw new BizException(ErrorCode.INVALID_PARAM, QUESTION_REQUIRED_MESSAGE);
        }
        String question = request.getQuestion().trim();
        String tenantId = TenantContext.getTenantId();
        Long userId = TenantContext.getUserId();
        String conversationId = resolveConversationId(request.getConversationId());
        TenantContext.setConversationId(conversationId);
        SqlExecutionResult executionResult;
        try {
            executionResult = sqlExecutor.execute(tenantId, question, "WEB");
        } catch (Exception ex) {
            executionResult = buildFailureResult(ex);
        }
        executionResult = sanitizeForUser(executionResult);
        String finalAnswer = resolveFinalAnswer(question, executionResult);
        String sqlText = normalizeSqlForView(executionResult);
        if (executionResult.isSuccess()) {
            chatMemoryService.saveSuccessExchange(
                    tenantId,
                    question,
                    finalAnswer,
                    executionResult.getSql(),
                    executionResult.getData()
            );
        }
        ChatHistory history = chatHistoryService.saveHistory(
                tenantId,
                userId,
                conversationId,
                question,
                finalAnswer,
                sqlText,
                executionResult.getData(),
                executionResult.getTrace()
        );
        return new ChatResult(
                finalAnswer,
                executionResult.getData(),
                sqlText,
                history.getId(),
                executionResult.getTrace(),
                conversationId
        );
    }

    private String normalizeSqlForView(SqlExecutionResult executionResult) {
        if (executionResult == null) {
            return "ERROR:UNKNOWN";
        }
        if (executionResult.isSuccess()) {
            return executionResult.getSql();
        }
        if (!StringUtils.hasText(executionResult.getErrorMessage())) {
            return "ERROR:" + EXECUTION_ERROR_MESSAGE;
        }
        return "ERROR:" + executionResult.getErrorMessage();
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

    private String resolveFinalAnswer(String question, SqlExecutionResult executionResult) {
        if (executionResult != null
                && !executionResult.isSuccess()
                && TENANT_DB_CONFIG_USER_MESSAGE.equals(executionResult.getErrorMessage())) {
            return TENANT_DB_CONFIG_USER_MESSAGE;
        }
        return resultRefiner.refine(question, executionResult);
    }

    private SqlExecutionResult buildFailureResult(Exception ex) {
        if (isTenantDbConfigMissing(ex)) {
            return SqlExecutionResult.failure(null, TENANT_DB_CONFIG_USER_MESSAGE, buildTenantConfigMissingTrace());
        }
        return SqlExecutionResult.failure(null, EXECUTION_ERROR_MESSAGE, buildFallbackTrace());
    }

    private boolean isTenantDbConfigMissing(Exception ex) {
        if (!(ex instanceof BizException bizException)) {
            return false;
        }
        return bizException.getErrorCode() == ErrorCode.TENANT_DB_CONFIG_NOT_FOUND;
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

    private String resolveConversationId(String conversationId) {
        if (StringUtils.hasText(conversationId)) {
            return conversationId.trim();
        }
        return UUID.randomUUID().toString();
    }
}
