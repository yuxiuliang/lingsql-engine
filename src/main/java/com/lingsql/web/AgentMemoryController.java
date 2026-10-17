package com.lingsql.web;

import com.lingsql.agent.memory.AgentMemoryService;
import com.lingsql.agent.memory.MemorySourceType;
import com.lingsql.agent.memory.MemoryTier;
import com.lingsql.agent.memory.model.AgentMemoryCorrectRequest;
import com.lingsql.agent.memory.model.AgentMemorySaveRequest;
import com.lingsql.agent.memory.model.AgentMemoryView;
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
import java.util.stream.Collectors;

/**
 * 用户长期记忆接口：用户管理自己的永久记忆，写入即确认，纠正生成新版本。
 *
 * @author 余炘洋
 * @date 2026-10-18
 */
@RestController
@RequestMapping("/api/v1/chat/memories")
@RequiredArgsConstructor
public class AgentMemoryController {

    private final AgentMemoryService agentMemoryService;

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
}
