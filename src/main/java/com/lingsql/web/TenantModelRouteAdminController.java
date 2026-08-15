package com.lingsql.web;

import com.lingsql.agent.TenantModelRouting;
import com.lingsql.agent.model.TenantModelRouteCommand;
import com.lingsql.agent.model.TenantModelRouteView;
import com.lingsql.audit.service.AuditOperationExecutor;
import com.lingsql.common.result.ApiResponse;
import com.lingsql.rbac.security.RbacSecurityUtils;
import com.lingsql.rbac.security.SecureTenantResolver;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 租户模型授权路由管理入口。
 *
 * @author 余炘洋
 * @date 2026-08-16
 */
@RestController
@RequestMapping("/api/v1/admin/tenant-model-routes")
@RequiredArgsConstructor
public class TenantModelRouteAdminController {

    private final TenantModelRouting tenantModelRouting;
    private final AuditOperationExecutor auditOperationExecutor;

    @GetMapping("/{tenantId}")
    @PreAuthorize("hasAuthority('ADMIN:MODEL:READ')")
    public ApiResponse<List<TenantModelRouteView>> list(@PathVariable String tenantId) {
        requireSysAdmin();
        return ApiResponse.success(tenantModelRouting.list(tenantId));
    }

    @PutMapping("/{tenantId}")
    @PreAuthorize("hasAuthority('ADMIN:MODEL:WRITE')")
    public ApiResponse<List<TenantModelRouteView>> replace(
            @PathVariable String tenantId,
            @RequestBody List<TenantModelRouteCommand> commands,
            HttpServletRequest http
    ) {
        requireSysAdmin();
        List<Long> modelConfigIds = commands == null
                ? List.of()
                : commands.stream().filter(command -> command != null).map(TenantModelRouteCommand::modelConfigId).toList();
        return auditOperationExecutor.execute(
                "TENANT_MODEL_ROUTE_REPLACE",
                "TENANT_MODEL_ROUTE",
                Map.of("tenantId", tenantId, "modelConfigIds", modelConfigIds),
                http,
                () -> tenantModelRouting.replace(tenantId, commands),
                result -> Map.of("tenantId", tenantId, "modelConfigIds", modelConfigIds)
        );
    }

    private void requireSysAdmin() {
        if (!SecureTenantResolver.isSysAdmin(RbacSecurityUtils.currentSession())) {
            throw new AccessDeniedException("无权限访问");
        }
    }
}
