package com.lingsql.web;

import com.lingsql.common.result.ApiResponse;
import com.lingsql.context.TenantContext;
import com.lingsql.executor.ResultRefiner;
import com.lingsql.executor.SmartSqlExecutor;
import com.lingsql.executor.SqlExecutionResult;
import com.lingsql.web.dto.ChatRequest;
import com.lingsql.web.dto.ChatResult;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 问答接口控制器。
 *
 * @author 余炘洋
 * @date 2026-01-22
 */
@RestController
@RequestMapping("/api/v1/chat")
@RequiredArgsConstructor
public class ChatController {

    private final SmartSqlExecutor sqlExecutor;
    private final ResultRefiner resultRefiner;

    /**
     * 核心问答接口。
     * 请求头需携带: X-Tenant-Id
     *
     * @param request 请求体
     * @return 问答结果
     * @author 余炘洋
     * @date 2026-01-22
     */
    @PostMapping("/ask")
    public ApiResponse<ChatResult> ask(@RequestBody ChatRequest request) {
        String tenantId = TenantContext.getTenantId();
        SqlExecutionResult executionResult;
        try {
            executionResult = sqlExecutor.execute(tenantId, request.getQuestion());
        } catch (Exception ex) {
            executionResult = SqlExecutionResult.failure(null, ex.getMessage());
        }
        String finalAnswer = resultRefiner.refine(request.getQuestion(), executionResult);
        ChatResult chatResult = new ChatResult(finalAnswer, executionResult.getData(), executionResult.getSql());
        return ApiResponse.success(chatResult);
    }
}
