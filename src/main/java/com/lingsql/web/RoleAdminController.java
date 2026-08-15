package com.lingsql.web;

import com.lingsql.audit.service.AuditOperationExecutor;
import com.lingsql.common.result.ApiResponse;
import com.lingsql.rbac.model.DataScopePreviewView;
import com.lingsql.rbac.model.DataScopeBatchPreviewView;
import com.lingsql.rbac.model.RoleCreateRequest;
import com.lingsql.rbac.model.RoleDataScopePreviewRequest;
import com.lingsql.rbac.model.RoleScopeReleaseCreateRequest;
import com.lingsql.rbac.model.RoleScopeReleaseRollbackRequest;
import com.lingsql.rbac.model.RoleScopeReleaseReviewRequest;
import com.lingsql.rbac.model.RoleScopeReleaseView;
import com.lingsql.rbac.model.RoleUpdateRequest;
import com.lingsql.rbac.model.RoleView;
import com.lingsql.rbac.security.RbacSecurityUtils;
import com.lingsql.rbac.security.RbacUserSession;
import com.lingsql.rbac.security.SecureTenantResolver;
import com.lingsql.rbac.service.DataScopePreviewService;
import com.lingsql.rbac.service.RoleAdminService;
import com.lingsql.rbac.service.RoleScopeReleaseService;
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
    private final DataScopePreviewService dataScopePreviewService;
    private final RoleScopeReleaseService roleScopeReleaseService;
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
     * 预览角色数据权限命中范围。
     *
     * @param request 预览请求
     * @return 预览结果
     * @author 余炘洋
     * @date 2026-04-12
     */
    @PostMapping("/preview-data-scope")
    @PreAuthorize("hasAuthority('ADMIN:ROLE:READ')")
    public ApiResponse<DataScopePreviewView> previewDataScope(@RequestBody RoleDataScopePreviewRequest request) {
        RbacUserSession session = requireSession();
        boolean sysAdmin = SecureTenantResolver.isSysAdmin(session);
        return ApiResponse.success(dataScopePreviewService.previewRoleScope(request, session.getTenantId(), sysAdmin));
    }

    /**
     * 批量预览角色数据权限。
     *
     * @param request 预览请求
     * @return 批量结果
     * @author 余炘洋
     * @date 2026-04-13
     */
    @PostMapping("/preview-data-scope/batch")
    @PreAuthorize("hasAuthority('ADMIN:ROLE:READ')")
    public ApiResponse<DataScopeBatchPreviewView> previewDataScopeBatch(@RequestBody RoleDataScopePreviewRequest request) {
        RbacUserSession session = requireSession();
        boolean sysAdmin = SecureTenantResolver.isSysAdmin(session);
        return ApiResponse.success(dataScopePreviewService.previewRoleScopeBatch(request, session.getTenantId(), sysAdmin));
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
     * 查询角色数据权限发布单列表。
     *
     * @param tenantId 租户ID
     * @param status 发布状态
     * @return 发布单列表
     * @author 余炘洋
     * @date 2026-04-13
     */
    @GetMapping("/scope-releases")
    @PreAuthorize("hasAuthority('ADMIN:ROLE:READ')")
    public ApiResponse<List<RoleScopeReleaseView>> listScopeReleases(@RequestParam(required = false) String tenantId,
                                                                     @RequestParam(required = false) String status) {
        RbacUserSession session = requireSession();
        boolean sysAdmin = SecureTenantResolver.isSysAdmin(session);
        String resolvedTenant = sysAdmin ? normalize(tenantId) : session.getTenantId();
        return ApiResponse.success(roleScopeReleaseService.listRecords(resolvedTenant, status, session.getTenantId(), sysAdmin));
    }

    /**
     * 创建角色数据权限发布单。
     *
     * @param request 请求体
     * @param http 请求
     * @return 发布单
     * @author 余炘洋
     * @date 2026-04-13
     */
    @PostMapping("/scope-releases")
    @PreAuthorize("hasAuthority('ADMIN:ROLE:WRITE')")
    public ApiResponse<RoleScopeReleaseView> createScopeRelease(@RequestBody RoleScopeReleaseCreateRequest request,
                                                                HttpServletRequest http) {
        RbacUserSession session = requireSession();
        boolean sysAdmin = SecureTenantResolver.isSysAdmin(session);
        return auditOperationExecutor.execute(
                "ROLE_SCOPE_RELEASE_CREATE",
                "ROLE_SCOPE_RELEASE",
                request,
                http,
                () -> roleScopeReleaseService.createRecord(request, session.getTenantId(), sysAdmin, session)
        );
    }

    /**
     * 审核角色数据权限发布单。
     *
     * @param id 发布单ID
     * @param request 请求体
     * @param http 请求
     * @return 发布单
     * @author 余炘洋
     * @date 2026-04-13
     */
    @PutMapping("/scope-releases/{id}/review")
    @PreAuthorize("hasAuthority('ADMIN:ROLE:WRITE')")
    public ApiResponse<RoleScopeReleaseView> reviewScopeRelease(@PathVariable Long id,
                                                                @RequestBody RoleScopeReleaseReviewRequest request,
                                                                HttpServletRequest http) {
        RbacUserSession session = requireSession();
        boolean sysAdmin = SecureTenantResolver.isSysAdmin(session);
        return auditOperationExecutor.execute(
                "ROLE_SCOPE_RELEASE_REVIEW",
                "ROLE_SCOPE_RELEASE",
                request,
                http,
                () -> roleScopeReleaseService.reviewRecord(id, request, session.getTenantId(), sysAdmin, session)
        );
    }

    /**
     * 应用角色数据权限发布单。
     *
     * @param id 发布单ID
     * @param http 请求
     * @return 发布单
     * @author 余炘洋
     * @date 2026-04-13
     */
    @PutMapping("/scope-releases/{id}/apply")
    @PreAuthorize("hasAuthority('ADMIN:ROLE:WRITE')")
    public ApiResponse<RoleScopeReleaseView> applyScopeRelease(@PathVariable Long id, HttpServletRequest http) {
        RbacUserSession session = requireSession();
        boolean sysAdmin = SecureTenantResolver.isSysAdmin(session);
        return auditOperationExecutor.execute(
                "ROLE_SCOPE_RELEASE_APPLY",
                "ROLE_SCOPE_RELEASE",
                "id=" + id,
                http,
                () -> roleScopeReleaseService.applyRecord(id, session.getTenantId(), sysAdmin, session)
        );
    }

    /**
     * 基于已应用发布单生成回滚单。
     *
     * @param id      源发布单ID
     * @param request 回滚请求
     * @param http    请求
     * @return 新发布单
     * @author 余炘洋
     * @date 2026-04-13
     */
    @PostMapping("/scope-releases/{id}/rollback")
    @PreAuthorize("hasAuthority('ADMIN:ROLE:WRITE')")
    public ApiResponse<RoleScopeReleaseView> createRollbackScopeRelease(@PathVariable Long id,
                                                                        @RequestBody(required = false) RoleScopeReleaseRollbackRequest request,
                                                                        HttpServletRequest http) {
        RbacUserSession session = requireSession();
        boolean sysAdmin = SecureTenantResolver.isSysAdmin(session);
        return auditOperationExecutor.execute(
                "ROLE_SCOPE_RELEASE_ROLLBACK_CREATE",
                "ROLE_SCOPE_RELEASE",
                request,
                http,
                () -> roleScopeReleaseService.createRollbackRecord(id, request, session.getTenantId(), sysAdmin, session)
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
