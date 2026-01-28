package com.lingsql.web;

import com.lingsql.audit.service.AuditOperationExecutor;
import com.lingsql.common.result.ApiResponse;
import com.lingsql.rbac.model.RoleCreateRequest;
import com.lingsql.rbac.model.RoleUpdateRequest;
import com.lingsql.rbac.model.RoleView;
import com.lingsql.rbac.security.RbacSecurityUtils;
import com.lingsql.rbac.security.RbacUserSession;
import com.lingsql.rbac.security.SecureTenantResolver;
import com.lingsql.rbac.service.RoleAdminService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 角色管理接口。
 *
 * @author 余炘洋
 * @date 2026-01-26
 */
@RestController
@RequestMapping("/api/v1/admin/roles")
@RequiredArgsConstructor
public class RoleAdminController {

    private final RoleAdminService roleAdminService;
    private final AuditOperationExecutor auditOperationExecutor;

    /**
     * 查询角色列表。
     *
     * @param tenantId 租户ID
     * @return 角色列表
     * @author 余炘洋
     * @date 2026-01-26
     */
    @GetMapping
    @PreAuthorize("hasAuthority('ADMIN:ROLE:READ')")
    public ApiResponse<List<RoleView>> listRoles(@RequestParam(required = false) String tenantId) {
        RbacUserSession session = requireSession();
        boolean sysAdmin = SecureTenantResolver.isSysAdmin(session);
        String resolvedTenant = sysAdmin ? normalize(tenantId) : session.getTenantId();
        return ApiResponse.success(roleAdminService.listRoles(resolvedTenant, sysAdmin));
    }

    /**
     * 创建角色。
     *
     * @param request 创建请求
     * @param http    请求
     * @return 角色信息
     * @author 余炘洋
     * @date 2026-01-26
     */
    @PostMapping
    @PreAuthorize("hasAuthority('ADMIN:ROLE:WRITE')")
    public ApiResponse<RoleView> createRole(@Valid @RequestBody RoleCreateRequest request, HttpServletRequest http) {
        RbacUserSession session = requireSession();
        boolean sysAdmin = SecureTenantResolver.isSysAdmin(session);
        return auditOperationExecutor.execute(
                "ROLE_CREATE",
                "ROLE",
                request,
                http,
                () -> roleAdminService.createRole(request, session.getTenantId(), sysAdmin)
        );
    }

    /**
     * 更新角色。
     *
     * @param id      角色ID
     * @param request 更新请求
     * @param http    请求
     * @return 角色信息
     * @author 余炘洋
     * @date 2026-01-26
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMIN:ROLE:WRITE')")
    public ApiResponse<RoleView> updateRole(@PathVariable Long id, @RequestBody RoleUpdateRequest request, HttpServletRequest http) {
        RbacUserSession session = requireSession();
        boolean sysAdmin = SecureTenantResolver.isSysAdmin(session);
        return auditOperationExecutor.execute(
                "ROLE_UPDATE",
                "ROLE",
                request,
                http,
                () -> roleAdminService.updateRole(id, request, session.getTenantId(), sysAdmin)
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


    /**
     * 规范化字符串。
     *
     * @param value 输入
     * @return 规范化结果
     * @author 余炘洋
     * @date 2026-01-26
     */
    private String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }
}
