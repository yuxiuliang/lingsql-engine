package com.lingsql.web;

import com.lingsql.audit.service.AuditOperationExecutor;
import com.lingsql.common.result.ApiResponse;
import com.lingsql.rbac.model.PermissionView;
import com.lingsql.rbac.model.TenantPermissionRequest;
import com.lingsql.rbac.security.RbacSecurityUtils;
import com.lingsql.rbac.security.RbacUserSession;
import com.lingsql.rbac.security.SecureTenantResolver;
import com.lingsql.rbac.service.PermissionAdminService;
import com.lingsql.rbac.service.TenantPermissionService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 权限管理接口。
 *
 * @author 余炘洋
 * @date 2026-01-26
 */
@RestController
@RequestMapping("/api/v1/admin/permissions")
@RequiredArgsConstructor
public class PermissionAdminController {

    private final PermissionAdminService permissionAdminService;
    private final TenantPermissionService tenantPermissionService;
    private final AuditOperationExecutor auditOperationExecutor;

    /**
     * 查询权限列表（租户管理员仅返回权限池）。
     *
     * @return 权限列表
     * @author 余炘洋
     * @date 2026-01-26
     */
    @GetMapping
    @PreAuthorize("hasAuthority('ADMIN:PERM:READ')")
    public ApiResponse<List<PermissionView>> listPermissions() {
        RbacUserSession session = requireSession();
        boolean sysAdmin = SecureTenantResolver.isSysAdmin(session);
        return ApiResponse.success(permissionAdminService.listPermissions(session.getTenantId(), sysAdmin));
    }

    /**
     * 查询租户权限池。
     *
     * @param tenantId 租户ID
     * @return 权限列表
     * @author 余炘洋
     * @date 2026-01-26
     */
    @GetMapping("/pool/{tenantId}")
    @PreAuthorize("hasAuthority('ADMIN:TENANT:POOL')")
    public ApiResponse<List<PermissionView>> listTenantPool(@PathVariable String tenantId) {
        RbacUserSession session = requireSession();
        if (!SecureTenantResolver.isSysAdmin(session)) {
            return ApiResponse.forbidden("无权限访问");
        }
        return ApiResponse.success(permissionAdminService.listTenantPool(tenantId));
    }

    /**
     * 更新租户权限池。
     *
     * @param tenantId 租户ID
     * @param request  请求体
     * @param http     请求
     * @return 更新结果
     * @author 余炘洋
     * @date 2026-01-26
     */
    @PutMapping("/pool/{tenantId}")
    @PreAuthorize("hasAuthority('ADMIN:TENANT:POOL')")
    public ApiResponse<String> updateTenantPool(@PathVariable String tenantId, @RequestBody TenantPermissionRequest request, HttpServletRequest http) {
        RbacUserSession session = requireSession();
        if (!SecureTenantResolver.isSysAdmin(session)) {
            return ApiResponse.forbidden("无权限访问");
        }
        return auditOperationExecutor.executeVoid(
                "TENANT_PERMISSION_UPDATE",
                tenantId,
                request,
                http,
                () -> tenantPermissionService.updateTenantPermissions(tenantId, request == null ? null : request.getPermIds()),
                "权限池已更新"
        );
    }

    /**
     * 获取当前会话。
     *
     * @return 会话信息
     * @author 余炘洋
     * @date 2026-01-26
     */
    private RbacUserSession requireSession() {
        RbacUserSession session = RbacSecurityUtils.currentSession();
        if (session == null) {
            throw new BadCredentialsException("未登录或登录已过期");
        }
        return session;
    }

}
