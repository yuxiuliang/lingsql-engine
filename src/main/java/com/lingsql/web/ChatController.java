package com.lingsql.web;

import com.lingsql.common.result.ApiResponse;
import com.lingsql.common.result.PageResult;
import com.lingsql.chat.model.ChatHistoryDetailView;
import com.lingsql.chat.model.ChatHistoryUpdateRequest;
import com.lingsql.chat.model.ChatHistoryView;
import com.lingsql.chat.model.ChatRequest;
import com.lingsql.chat.model.ChatResult;
import com.lingsql.chat.service.ChatApplicationService;
import com.lingsql.chat.service.ChatHistoryService;
import com.lingsql.context.TenantContext;
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

    private final ChatApplicationService chatApplicationService;
    private final ChatHistoryService chatHistoryService;

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
        return ApiResponse.success(chatApplicationService.ask(request));
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

}
