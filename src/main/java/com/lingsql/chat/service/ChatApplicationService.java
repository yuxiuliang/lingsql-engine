package com.lingsql.chat.service;

import com.lingsql.chat.entity.ChatHistory;
import com.lingsql.chat.model.ChatRequest;
import com.lingsql.chat.model.ChatResult;
import com.lingsql.context.TenantContext;
import com.lingsql.executor.ResultRefiner;
import com.lingsql.executor.SmartSqlExecutor;
import com.lingsql.executor.SqlExecutionResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 问答业务编排服务。
 *
 * @author 余炘洋
 * @date 2026-01-28
 */
@Service
@RequiredArgsConstructor
public class ChatApplicationService {

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
            executionResult = SqlExecutionResult.failure(null, ex.getMessage());
        }
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
                executionResult.getData()
        );
        return new ChatResult(finalAnswer, executionResult.getData(), sqlText, history.getId());
    }

    private String normalizeSqlForView(SqlExecutionResult executionResult) {
        if (executionResult == null) {
            return "ERROR:UNKNOWN";
        }
        if (executionResult.isSuccess()) {
            return executionResult.getSql();
        }
        String sql = executionResult.getSql();
        if (sql != null && sql.startsWith("ERROR")) {
            return sql;
        }
        String errorMessage = executionResult.getErrorMessage();
        if (errorMessage == null || errorMessage.isBlank()) {
            return "ERROR:UNKNOWN";
        }
        return "ERROR:" + errorMessage;
    }
}
