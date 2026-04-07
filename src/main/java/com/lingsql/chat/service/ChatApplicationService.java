package com.lingsql.chat.service;

import com.lingsql.chat.entity.ChatHistory;
import com.lingsql.chat.model.ChatRequest;
import com.lingsql.chat.model.ChatResult;
import com.lingsql.chat.model.ChatTraceStep;
import com.lingsql.context.TenantContext;
import com.lingsql.executor.ResultRefiner;
import com.lingsql.executor.SmartSqlExecutor;
import com.lingsql.executor.SqlExecutionResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

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
        String tenantId = TenantContext.getTenantId();
        Long userId = TenantContext.getUserId();
        SqlExecutionResult executionResult;
        try {
            executionResult = sqlExecutor.execute(tenantId, request.getQuestion(), "WEB");
        } catch (Exception ex) {
            executionResult = SqlExecutionResult.failure(null, EXECUTION_ERROR_MESSAGE, buildFallbackTrace());
        }
        executionResult = sanitizeForUser(executionResult);
        String finalAnswer = resultRefiner.refine(request.getQuestion(), executionResult);
        String sqlText = normalizeSqlForView(executionResult);
        if (executionResult.isSuccess()) {
            chatMemoryService.saveSuccessExchange(
                    tenantId,
                    request.getQuestion(),
                    finalAnswer,
                    executionResult.getSql(),
                    executionResult.getData()
            );
        }
        ChatHistory history = chatHistoryService.saveHistory(
                tenantId,
                userId,
                request.getQuestion(),
                finalAnswer,
                sqlText,
                executionResult.getData(),
                executionResult.getTrace()
        );
        return new ChatResult(finalAnswer, executionResult.getData(), sqlText, history.getId(), executionResult.getTrace());
    }

    private String normalizeSqlForView(SqlExecutionResult executionResult) {
        if (executionResult == null) {
            return "ERROR:UNKNOWN";
        }
        if (executionResult.isSuccess()) {
            return executionResult.getSql();
        }
        return "ERROR:" + EXECUTION_ERROR_MESSAGE;
    }

    private SqlExecutionResult sanitizeForUser(SqlExecutionResult executionResult) {
        if (executionResult == null || executionResult.isSuccess()) {
            return executionResult;
        }
        return SqlExecutionResult.failure(executionResult.getSql(), EXECUTION_ERROR_MESSAGE, executionResult.getTrace());
    }

    private List<ChatTraceStep> buildFallbackTrace() {
        return List.of(
                new ChatTraceStep("THOUGHT", "接收用户问题", "系统已接收问题并准备启动 SQL 执行链路。", "SUCCESS", 1),
                new ChatTraceStep("OBSERVATION", "执行链路异常", "后端在启动执行链路时发生异常，已返回安全失败提示。", "FAILURE", 1),
                new ChatTraceStep("FINAL", "结束处理", "本次请求未能完成查询，请稍后重试或补充更明确的筛选条件。", "FAILURE", 1)
        );
    }
}
