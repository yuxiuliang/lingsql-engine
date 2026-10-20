package com.lingsql.web;

import com.lingsql.agent.memory.AgentMemoryService;
import com.lingsql.agent.memory.MemoryCandidateService;
import com.lingsql.agent.memory.MemorySourceType;
import com.lingsql.agent.memory.MemoryTier;
import com.lingsql.agent.memory.model.AgentMemoryCorrectRequest;
import com.lingsql.agent.memory.model.AgentMemoryExportView;
import com.lingsql.agent.memory.model.AgentMemoryExtractRequest;
import com.lingsql.agent.memory.model.AgentMemoryRejectRequest;
import com.lingsql.agent.memory.model.AgentMemorySaveRequest;
import com.lingsql.agent.memory.model.AgentMemoryView;
import com.lingsql.agent.orchestration.AgentExecutionContext;
import com.lingsql.common.result.ApiResponse;
import com.lingsql.context.TenantContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 用户长期记忆接口：用户管理自己的永久记忆，写入即确认，纠正生成新版本；
 * 模型提炼的事实只进入候选池，必须由用户确认后才生成生效记忆。
 *
 * @author 余炘洋
 * @date 2026-10-18
 */
@RestController
@RequestMapping("/api/v1/chat/memories")
@RequiredArgsConstructor
public class AgentMemoryController {

    private final AgentMemoryService agentMemoryService;

    private final MemoryCandidateService memoryCandidateService;

    /**
     * 查询我的永久记忆。
     *
     * @return 记忆列表
     * @author 余炘洋
     * @date 2026-10-18
     */
    @GetMapping
    @PreAuthorize("hasAuthority('API:CHAT:MEMORY:READ')")
    public ApiResponse<List<AgentMemoryView>> list() {
        Long userId = TenantContext.getUserId();
        if (userId == null) {
            return ApiResponse.unauthorized("未登录或登录已过期");
        }
        String tenantId = TenantContext.getTenantId();
        List<AgentMemoryView> views = agentMemoryService.listOwn(tenantId, userId).stream()
                .map(AgentMemoryView::from)
                .collect(Collectors.toList());
        return ApiResponse.success(views);
    }

    /**
     * 写入我的永久记忆。
     *
     * @param request 请求体
     * @return 记忆详情
     * @author 余炘洋
     * @date 2026-10-18
     */
    @PostMapping
    @PreAuthorize("hasAuthority('API:CHAT:MEMORY:WRITE')")
    public ApiResponse<AgentMemoryView> create(@Valid @RequestBody AgentMemorySaveRequest request) {
        Long userId = TenantContext.getUserId();
        if (userId == null) {
            return ApiResponse.unauthorized("未登录或登录已过期");
        }
        String tenantId = TenantContext.getTenantId();
        return ApiResponse.success(AgentMemoryView.from(agentMemoryService.remember(
                tenantId,
                userId,
                MemoryTier.PERMANENT,
                MemorySourceType.USER_CONFIRMED,
                request.getFactText(),
                request.getRetentionDays(),
                String.valueOf(userId)
        )));
    }

    /**
     * 纠正我的永久记忆，旧事实失效并生成新版本。
     *
     * @param id      记忆主键
     * @param request 请求体
     * @return 新版本记忆
     * @author 余炘洋
     * @date 2026-10-18
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('API:CHAT:MEMORY:WRITE')")
    public ApiResponse<AgentMemoryView> correct(@PathVariable Long id,
                                               @Valid @RequestBody AgentMemoryCorrectRequest request) {
        Long userId = TenantContext.getUserId();
        if (userId == null) {
            return ApiResponse.unauthorized("未登录或登录已过期");
        }
        String tenantId = TenantContext.getTenantId();
        return ApiResponse.success(AgentMemoryView.from(agentMemoryService.correctOwn(
                tenantId,
                userId,
                id,
                request.getFactText(),
                request.getRetentionDays()
        )));
    }

    /**
     * 撤销我的永久记忆。
     *
     * @param id 记忆主键
     * @return 撤销后的记忆
     * @author 余炘洋
     * @date 2026-10-18
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('API:CHAT:MEMORY:WRITE')")
    public ApiResponse<AgentMemoryView> revoke(@PathVariable Long id) {
        Long userId = TenantContext.getUserId();
        if (userId == null) {
            return ApiResponse.unauthorized("未登录或登录已过期");
        }
        String tenantId = TenantContext.getTenantId();
        return ApiResponse.success(AgentMemoryView.from(agentMemoryService.revokeOwn(tenantId, userId, id)));
    }

    /**
     * 查询我的待确认记忆候选。
     *
     * @return 候选列表
     * @author 余炘洋
     * @date 2026-10-19
     */
    @GetMapping("/candidates")
    @PreAuthorize("hasAuthority('API:CHAT:MEMORY:READ')")
    public ApiResponse<List<AgentMemoryView>> listCandidates() {
        Long userId = TenantContext.getUserId();
        if (userId == null) {
            return ApiResponse.unauthorized("未登录或登录已过期");
        }
        List<AgentMemoryView> views = memoryCandidateService
                .listOwnCandidates(TenantContext.getTenantId(), userId)
                .stream()
                .map(AgentMemoryView::from)
                .collect(Collectors.toList());
        return ApiResponse.success(views);
    }

    /**
     * 从当前会话窗口提炼记忆候选：候选默认不生效，需用户确认后才写入长期记忆。
     *
     * @param request 请求体
     * @return 本次新增的候选
     * @author 余炘洋
     * @date 2026-10-19
     */
    @PostMapping("/candidates/extract")
    @PreAuthorize("hasAuthority('API:CHAT:MEMORY:WRITE')")
    public ApiResponse<List<AgentMemoryView>> extractCandidates(@Valid @RequestBody AgentMemoryExtractRequest request) {
        Long userId = TenantContext.getUserId();
        if (userId == null) {
            return ApiResponse.unauthorized("未登录或登录已过期");
        }
        String tenantId = TenantContext.getTenantId();
        AgentExecutionContext context = new AgentExecutionContext(
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                tenantId,
                userId,
                TenantContext.getConversationId()
        );
        List<AgentMemoryView> views = memoryCandidateService
                .extractCandidates(context, request.getSourceRef(), request.getMaxCandidates())
                .stream()
                .map(AgentMemoryView::from)
                .collect(Collectors.toList());
        return ApiResponse.success(views);
    }

    /**
     * 确认采纳我的记忆候选：生成生效记忆，候选记录标记为已采纳。
     *
     * @param id 候选主键
     * @return 生成的生效记忆
     * @author 余炘洋
     * @date 2026-10-19
     */
    @PostMapping("/candidates/{id}/approve")
    @PreAuthorize("hasAuthority('API:CHAT:MEMORY:WRITE')")
    public ApiResponse<AgentMemoryView> approveCandidate(@PathVariable Long id) {
        Long userId = TenantContext.getUserId();
        if (userId == null) {
            return ApiResponse.unauthorized("未登录或登录已过期");
        }
        String tenantId = TenantContext.getTenantId();
        return ApiResponse.success(AgentMemoryView.from(
                memoryCandidateService.approveByUser(tenantId, userId, id)));
    }

    /**
     * 拒绝我的记忆候选：只更新候选状态，不生成任何生效记忆。
     *
     * @param id      候选主键
     * @param request 请求体，拒绝原因可为空
     * @return 候选记录
     * @author 余炘洋
     * @date 2026-10-19
     */
    @PostMapping("/candidates/{id}/reject")
    @PreAuthorize("hasAuthority('API:CHAT:MEMORY:WRITE')")
    public ApiResponse<AgentMemoryView> rejectCandidate(@PathVariable Long id,
                                                       @Valid @RequestBody(required = false)
                                                       AgentMemoryRejectRequest request) {
        Long userId = TenantContext.getUserId();
        if (userId == null) {
            return ApiResponse.unauthorized("未登录或登录已过期");
        }
        String tenantId = TenantContext.getTenantId();
        String reason = request == null ? null : request.getReason();
        return ApiResponse.success(AgentMemoryView.from(
                memoryCandidateService.rejectByUser(tenantId, userId, id, reason)));
    }

    /**
     * 导出我的长期记忆与候选：内容与来源、时间、保留期一致，用于用户自查与留档。
     *
     * @return 导出视图
     * @author 余炘洋
     * @date 2026-10-19
     */
    @GetMapping("/export")
    @PreAuthorize("hasAuthority('API:CHAT:MEMORY:READ')")
    public ApiResponse<AgentMemoryExportView> exportOwn() {
        Long userId = TenantContext.getUserId();
        if (userId == null) {
            return ApiResponse.unauthorized("未登录或登录已过期");
        }
        return ApiResponse.success(agentMemoryService.exportOwn(TenantContext.getTenantId(), userId));
    }
}
