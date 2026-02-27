package com.lingsql.web;

import com.lingsql.audit.service.AuditOperationExecutor;
import com.lingsql.common.result.ApiResponse;
import com.lingsql.rbac.security.RbacSecurityUtils;
import com.lingsql.rbac.security.RbacUserSession;
import com.lingsql.tenant.model.TenantCreateRequest;
import com.lingsql.tenant.model.TenantLifecycleSummaryView;
import com.lingsql.tenant.model.TenantUpdateRequest;
import com.lingsql.tenant.model.TenantView;
import com.lingsql.tenant.service.TenantAdminService;
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

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * 租户管理接口。
 *
 * @author 余炘洋
 * @date 2026-01-26
 */
@RestController
@RequestMapping("/api/v1/admin/tenants")
@RequiredArgsConstructor
public class TenantAdminController {

    private final TenantAdminService tenantAdminService;
    private final AuditOperationExecutor auditOperationExecutor;

    /**
     * 查询租户列表。
     *
     * @return 租户列表
     * @author 余炘洋
     * @date 2026-01-26
     */
    @GetMapping
    @PreAuthorize("hasAuthority('ADMIN:TENANT:READ')")
    public ApiResponse<List<TenantView>> listTenants() {
        if (!isSysAdmin()) {
            return ApiResponse.forbidden("无权限访问");
        }
        return ApiResponse.success(tenantAdminService.listTenants());
    }

    /**
     * 查询租户生命周期汇总。
     *
     * @return 生命周期汇总
     * @author 余炘洋
     * @date 2026-02-27
     */
    @GetMapping("/lifecycle/summary")
    @PreAuthorize("hasAuthority('ADMIN:TENANT:READ')")
    public ApiResponse<TenantLifecycleSummaryView> lifecycleSummary() {
        if (!isSysAdmin()) {
            return ApiResponse.forbidden("无权限访问");
        }
        return ApiResponse.success(tenantAdminService.lifecycleSummary());
    }

    /**
     * 创建租户。
     *
     * @param request 创建请求
     * @param http    请求
     * @return 创建结果
     * @author 余炘洋
     * @date 2026-01-26
     */
    @PostMapping
    @PreAuthorize("hasAuthority('ADMIN:TENANT:WRITE')")
    public ApiResponse<TenantView> createTenant(@RequestBody TenantCreateRequest request, HttpServletRequest http) {
        if (!isSysAdmin()) {
            return ApiResponse.forbidden("无权限访问");
        }
        return auditOperationExecutor.execute(
                "TENANT_CREATE",
                "TENANT",
                request,
                http,
                () -> tenantAdminService.createTenant(request)
        );
    }

    /**
     * 更新租户。
     *
     * @param id      租户ID
     * @param request 更新请求
     * @param http    请求
     * @return 更新结果
     * @author 余炘洋
     * @date 2026-01-26
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMIN:TENANT:WRITE')")
    public ApiResponse<TenantView> updateTenant(@PathVariable Long id, @RequestBody TenantUpdateRequest request, HttpServletRequest http) {
        if (!isSysAdmin()) {
            return ApiResponse.forbidden("无权限访问");
        }
        return auditOperationExecutor.execute(
                "TENANT_UPDATE",
                "TENANT",
                request,
                http,
                () -> tenantAdminService.updateTenant(id, request)
        );
    }

    /**
     * 删除租户。
     *
     * @param id 租户ID
     * @param http 请求
     * @return 删除结果
     * @author 余炘洋
     * @date 2026-01-26
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMIN:TENANT:DELETE')")
    public ApiResponse<String> deleteTenant(@PathVariable Long id, HttpServletRequest http) {
        if (!isSysAdmin()) {
            return ApiResponse.forbidden("无权限访问");
        }
        return auditOperationExecutor.executeVoid(
                "TENANT_DELETE",
                "TENANT",
                "id=" + id,
                http,
                () -> tenantAdminService.deleteTenant(id),
                "租户已删除"
        );
    }


    /**
     * 判断是否系统管理员。
     *
     * @return 是否系统管理员
     * @author 余炘洋
     * @date 2026-01-26
     */
    private boolean isSysAdmin() {
        RbacUserSession session = RbacSecurityUtils.currentSession();
        return com.lingsql.rbac.security.SecureTenantResolver.isSysAdmin(session);
    }
}
