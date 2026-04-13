package com.lingsql.web;

import com.lingsql.audit.service.AuditOperationExecutor;
import com.lingsql.common.result.ApiResponse;
import com.lingsql.common.result.PageResult;
import com.lingsql.rbac.model.DataScopeBatchPreviewView;
import com.lingsql.rbac.model.DataScopePreviewView;
import com.lingsql.rbac.model.UserCreateRequest;
import com.lingsql.rbac.model.UserDataScopePreviewRequest;
import com.lingsql.rbac.model.UserPasswordResetRequest;
import com.lingsql.rbac.model.UserScopeReleaseCreateRequest;
import com.lingsql.rbac.model.UserScopeReleaseRollbackRequest;
import com.lingsql.rbac.model.UserScopeReleaseReviewRequest;
import com.lingsql.rbac.model.UserScopeReleaseView;
import com.lingsql.rbac.model.UserUpdateRequest;
import com.lingsql.rbac.model.UserView;
import com.lingsql.rbac.security.RbacSecurityUtils;
import com.lingsql.rbac.security.RbacUserSession;
import com.lingsql.rbac.security.SecureTenantResolver;
import com.lingsql.rbac.service.DataScopePreviewService;
import com.lingsql.rbac.service.UserAdminService;
import com.lingsql.rbac.service.UserScopeReleaseService;
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

/**
 * 用户管理接口。
 *
 * @author 余炘洋
 * @date 2026-01-26
 */
@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
public class UserAdminController {

    private final UserAdminService userAdminService;
    private final DataScopePreviewService dataScopePreviewService;
    private final UserScopeReleaseService userScopeReleaseService;
    private final AuditOperationExecutor auditOperationExecutor;

    /**
     * 查询用户列表。
     *
     * @param tenantId 租户ID
     * @param keyword  关键词
     * @param page     页码
     * @param size     每页大小
     * @return 用户分页
     * @author 余炘洋
     * @date 2026-01-26
     */
    @GetMapping
    @PreAuthorize("hasAuthority('ADMIN:USER:READ')")
    public ApiResponse<PageResult<UserView>> listUsers(
            @RequestParam(required = false) String tenantId,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        RbacUserSession session = requireSession();
        boolean sysAdmin = SecureTenantResolver.isSysAdmin(session);
        String resolvedTenant = sysAdmin ? normalize(tenantId) : session.getTenantId();
        return ApiResponse.success(userAdminService.listUsers(resolvedTenant, keyword, page, size));
    }

    /**
     * 创建用户。
     *
     * @param request 创建请求
     * @param http    请求
     * @return 用户信息
     * @author 余炘洋
     * @date 2026-01-26
     */
    @PostMapping
    @PreAuthorize("hasAuthority('ADMIN:USER:WRITE')")
    public ApiResponse<UserView> createUser(@Valid @RequestBody UserCreateRequest request, HttpServletRequest http) {
        RbacUserSession session = requireSession();
        boolean sysAdmin = SecureTenantResolver.isSysAdmin(session);
        return auditOperationExecutor.execute(
                "USER_CREATE",
                "USER",
                request,
                http,
                () -> userAdminService.createUser(request, session.getTenantId(), sysAdmin)
        );
    }

    /**
     * 更新用户。
     *
     * @param id      用户ID
     * @param request 更新请求
     * @param http    请求
     * @return 用户信息
     * @author 余炘洋
     * @date 2026-01-26
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMIN:USER:WRITE')")
    public ApiResponse<UserView> updateUser(@PathVariable Long id, @RequestBody UserUpdateRequest request, HttpServletRequest http) {
        RbacUserSession session = requireSession();
        boolean sysAdmin = SecureTenantResolver.isSysAdmin(session);
        return auditOperationExecutor.execute(
                "USER_UPDATE",
                "USER",
                request,
                http,
                () -> userAdminService.updateUser(id, request, session.getTenantId(), sysAdmin)
        );
    }

    /**
     * 重置密码。
     *
     * @param id      用户ID
     * @param request 重置请求
     * @param http    请求
     * @return 重置结果
     * @author 余炘洋
     * @date 2026-01-26
     */
    @PutMapping("/{id}/password")
    @PreAuthorize("hasAuthority('ADMIN:USER:RESET')")
    public ApiResponse<String> resetPassword(@PathVariable Long id, @Valid @RequestBody UserPasswordResetRequest request, HttpServletRequest http) {
        RbacUserSession session = requireSession();
        boolean sysAdmin = SecureTenantResolver.isSysAdmin(session);
        return auditOperationExecutor.executeVoid(
                "USER_PASSWORD_RESET",
                "USER",
                request,
                http,
                () -> userAdminService.resetPassword(id, request, session.getTenantId(), sysAdmin),
                "密码已重置"
        );
    }

    /**
     * 预览用户数据权限命中范围。
     *
     * @param request 预览请求
     * @return 预览结果
     * @author 余炘洋
     * @date 2026-04-12
     */
    @PostMapping("/preview-data-scope")
    @PreAuthorize("hasAuthority('ADMIN:USER:READ')")
    public ApiResponse<DataScopePreviewView> previewDataScope(@RequestBody UserDataScopePreviewRequest request) {
        RbacUserSession session = requireSession();
        boolean sysAdmin = SecureTenantResolver.isSysAdmin(session);
        return ApiResponse.success(dataScopePreviewService.previewUserScope(request, session.getTenantId(), sysAdmin));
    }

    /**
     * 批量预览用户数据权限。
     *
     * @param request 预览请求
     * @return 批量结果
     * @author 余炘洋
     * @date 2026-04-13
     */
    @PostMapping("/preview-data-scope/batch")
    @PreAuthorize("hasAuthority('ADMIN:USER:READ')")
    public ApiResponse<DataScopeBatchPreviewView> previewDataScopeBatch(@RequestBody UserDataScopePreviewRequest request) {
        RbacUserSession session = requireSession();
        boolean sysAdmin = SecureTenantResolver.isSysAdmin(session);
        return ApiResponse.success(dataScopePreviewService.previewUserScopeBatch(request, session.getTenantId(), sysAdmin));
    }

    /**
     * 查询用户数据权限发布单列表。
     *
     * @param tenantId 租户ID
     * @param status 发布状态
     * @return 发布单列表
     * @author 余炘洋
     * @date 2026-04-13
     */
    @GetMapping("/scope-releases")
    @PreAuthorize("hasAuthority('ADMIN:USER:READ')")
    public ApiResponse<java.util.List<UserScopeReleaseView>> listScopeReleases(@RequestParam(required = false) String tenantId,
                                                                               @RequestParam(required = false) String status) {
        RbacUserSession session = requireSession();
        boolean sysAdmin = SecureTenantResolver.isSysAdmin(session);
        String resolvedTenant = sysAdmin ? normalize(tenantId) : session.getTenantId();
        return ApiResponse.success(userScopeReleaseService.listRecords(resolvedTenant, status, session.getTenantId(), sysAdmin));
    }

    /**
     * 创建用户数据权限发布单。
     *
     * @param request 请求体
     * @param http 请求
     * @return 发布单
     * @author 余炘洋
     * @date 2026-04-13
     */
    @PostMapping("/scope-releases")
    @PreAuthorize("hasAuthority('ADMIN:USER:WRITE')")
    public ApiResponse<UserScopeReleaseView> createScopeRelease(@RequestBody UserScopeReleaseCreateRequest request,
                                                                HttpServletRequest http) {
        RbacUserSession session = requireSession();
        boolean sysAdmin = SecureTenantResolver.isSysAdmin(session);
        return auditOperationExecutor.execute(
                "USER_SCOPE_RELEASE_CREATE",
                "USER_SCOPE_RELEASE",
                request,
                http,
                () -> userScopeReleaseService.createRecord(request, session.getTenantId(), sysAdmin, session)
        );
    }

    /**
     * 审核用户数据权限发布单。
     *
     * @param id 发布单ID
     * @param request 请求体
     * @param http 请求
     * @return 发布单
     * @author 余炘洋
     * @date 2026-04-13
     */
    @PutMapping("/scope-releases/{id}/review")
    @PreAuthorize("hasAuthority('ADMIN:USER:WRITE')")
    public ApiResponse<UserScopeReleaseView> reviewScopeRelease(@PathVariable Long id,
                                                                @RequestBody UserScopeReleaseReviewRequest request,
                                                                HttpServletRequest http) {
        RbacUserSession session = requireSession();
        boolean sysAdmin = SecureTenantResolver.isSysAdmin(session);
        return auditOperationExecutor.execute(
                "USER_SCOPE_RELEASE_REVIEW",
                "USER_SCOPE_RELEASE",
                request,
                http,
                () -> userScopeReleaseService.reviewRecord(id, request, session.getTenantId(), sysAdmin, session)
        );
    }

    /**
     * 应用用户数据权限发布单。
     *
     * @param id 发布单ID
     * @param http 请求
     * @return 发布单
     * @author 余炘洋
     * @date 2026-04-13
     */
    @PutMapping("/scope-releases/{id}/apply")
    @PreAuthorize("hasAuthority('ADMIN:USER:WRITE')")
    public ApiResponse<UserScopeReleaseView> applyScopeRelease(@PathVariable Long id, HttpServletRequest http) {
        RbacUserSession session = requireSession();
        boolean sysAdmin = SecureTenantResolver.isSysAdmin(session);
        return auditOperationExecutor.execute(
                "USER_SCOPE_RELEASE_APPLY",
                "USER_SCOPE_RELEASE",
                "id=" + id,
                http,
                () -> userScopeReleaseService.applyRecord(id, session.getTenantId(), sysAdmin, session)
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
    @PreAuthorize("hasAuthority('ADMIN:USER:WRITE')")
    public ApiResponse<UserScopeReleaseView> createRollbackScopeRelease(@PathVariable Long id,
                                                                        @RequestBody(required = false) UserScopeReleaseRollbackRequest request,
                                                                        HttpServletRequest http) {
        RbacUserSession session = requireSession();
        boolean sysAdmin = SecureTenantResolver.isSysAdmin(session);
        return auditOperationExecutor.execute(
                "USER_SCOPE_RELEASE_ROLLBACK_CREATE",
                "USER_SCOPE_RELEASE",
                request,
                http,
                () -> userScopeReleaseService.createRollbackRecord(id, request, session.getTenantId(), sysAdmin, session)
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
