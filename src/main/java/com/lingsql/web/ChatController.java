package com.lingsql.web;

import com.lingsql.common.result.ApiResponse;
import com.lingsql.common.result.PageResult;
import com.lingsql.chat.entity.ChatHistory;
import com.lingsql.chat.model.ChatHistoryDetailView;
import com.lingsql.chat.model.ChatHistoryView;
import com.lingsql.chat.service.ChatHistoryService;
import com.lingsql.chat.service.ChatMemoryService;
import com.lingsql.context.TenantContext;
import com.lingsql.executor.ResultRefiner;
import com.lingsql.executor.SmartSqlExecutor;
import com.lingsql.executor.SqlExecutionResult;
import com.lingsql.web.dto.ChatHistoryUpdateRequest;
import com.lingsql.web.dto.ChatRequest;
import com.lingsql.web.dto.ChatResult;
import lombok.RequiredArgsConstructor;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.security.access.prepost.PreAuthorize;

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
    private final ChatHistoryService chatHistoryService;
    private final ChatMemoryService chatMemoryService;

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
    @PreAuthorize("hasAuthority('API:CHAT:ASK')")
    public ApiResponse<ChatResult> ask(@Valid @RequestBody ChatRequest request) {
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
        ChatResult chatResult = new ChatResult(finalAnswer, executionResult.getData(), sqlText, history.getId());
        return ApiResponse.success(chatResult);
    }

    /**
     * 查询历史记录列表。
     *
     * @return 历史记录
     * @author 余炘洋
     * @date 2026-01-23
     */
    @GetMapping("/history")
    @PreAuthorize("hasAuthority('API:CHAT:HISTORY:READ')")
    public ApiResponse<PageResult<ChatHistoryView>> history(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword
    ) {
        String tenantId = TenantContext.getTenantId();
        Long userId = TenantContext.getUserId();
        return ApiResponse.success(chatHistoryService.listHistory(tenantId, userId, keyword, page, size));
    }

    /**
     * 查询历史记录详情。
     *
     * @param id 记录ID
     * @return 历史详情
     * @author 余炘洋
     * @date 2026-01-23
     */
    @GetMapping("/history/{id}")
    @PreAuthorize("hasAuthority('API:CHAT:HISTORY:READ')")
    public ApiResponse<ChatHistoryDetailView> historyDetail(@PathVariable Long id) {
        String tenantId = TenantContext.getTenantId();
        Long userId = TenantContext.getUserId();
        return ApiResponse.success(chatHistoryService.getHistoryDetail(tenantId, userId, id));
    }

    /**
     * 重命名历史记录。
     *
     * @param id      记录ID
     * @param request 更新请求
     * @return 更新结果
     * @author 余炘洋
     * @date 2026-01-23
     */
    @PutMapping("/history/{id}")
    @PreAuthorize("hasAuthority('API:CHAT:HISTORY:WRITE')")
    public ApiResponse<ChatHistoryView> renameHistory(@PathVariable Long id, @RequestBody ChatHistoryUpdateRequest request) {
        String tenantId = TenantContext.getTenantId();
        Long userId = TenantContext.getUserId();
        return ApiResponse.success(chatHistoryService.renameHistory(tenantId, userId, id, request.getQuestion()));
    }

    /**
     * 删除历史记录。
     *
     * @param id 记录ID
     * @return 删除结果
     * @author 余炘洋
     * @date 2026-01-23
     */
    @DeleteMapping("/history/{id}")
    @PreAuthorize("hasAuthority('API:CHAT:HISTORY:WRITE')")
    public ApiResponse<String> deleteHistory(@PathVariable Long id) {
        String tenantId = TenantContext.getTenantId();
        Long userId = TenantContext.getUserId();
        chatHistoryService.deleteHistory(tenantId, userId, id);
        return ApiResponse.success("历史记录已删除");
    }

    /**
     * 规范化 SQL 展示内容。
     *
     * @param executionResult 执行结果
     * @return SQL 文本
     * @author 余炘洋
     * @date 2026-01-23
     */
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
