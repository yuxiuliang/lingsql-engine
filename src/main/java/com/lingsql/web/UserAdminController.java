package com.lingsql.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingsql.audit.model.AuditLogCommand;
import com.lingsql.audit.service.AuditLogService;
import com.lingsql.common.result.ApiResponse;
import com.lingsql.common.result.PageResult;
import com.lingsql.rbac.model.UserCreateRequest;
import com.lingsql.rbac.model.UserPasswordResetRequest;
import com.lingsql.rbac.model.UserUpdateRequest;
import com.lingsql.rbac.model.UserView;
import com.lingsql.rbac.security.RbacSecurityUtils;
import com.lingsql.rbac.security.RbacUserSession;
import com.lingsql.rbac.security.SecureTenantResolver;
import com.lingsql.rbac.service.UserAdminService;
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
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;

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
        try {
            UserView view = userAdminService.createUser(request, session.getTenantId(), sysAdmin);
            recordAudit(session, "USER_CREATE", "USER", request, true, null, http);
            return ApiResponse.success(view);
        } catch (Exception ex) {
            recordAudit(session, "USER_CREATE", "USER", request, false, ex.getMessage(), http);
            return ApiResponse.error(ex.getMessage());
        }
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
        try {
            UserView view = userAdminService.updateUser(id, request, session.getTenantId(), sysAdmin);
            recordAudit(session, "USER_UPDATE", "USER", request, true, null, http);
            return ApiResponse.success(view);
        } catch (Exception ex) {
            recordAudit(session, "USER_UPDATE", "USER", request, false, ex.getMessage(), http);
            return ApiResponse.error(ex.getMessage());
        }
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
        try {
            userAdminService.resetPassword(id, request, session.getTenantId(), sysAdmin);
            recordAudit(session, "USER_PASSWORD_RESET", "USER", request, true, null, http);
            return ApiResponse.success("密码已重置");
        } catch (Exception ex) {
            recordAudit(session, "USER_PASSWORD_RESET", "USER", request, false, ex.getMessage(), http);
            return ApiResponse.error(ex.getMessage());
        }
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
     * 记录审计日志。
     *
     * @param session    会话
     * @param action     动作
     * @param resource   资源
     * @param request    请求体
     * @param success    是否成功
     * @param error      错误信息
     * @param http       请求
     * @author 余炘洋
     * @date 2026-01-26
     */
    private void recordAudit(RbacUserSession session, String action, String resource, Object request, boolean success, String error, HttpServletRequest http) {
        AuditLogCommand command = new AuditLogCommand();
        command.setTenantId(session.getTenantId());
        command.setUserId(session.getUserId());
        command.setUsername(session.getUsername());
        command.setAction(action);
        command.setResource(resource);
        command.setDetail(serialize(request));
        command.setSuccess(success);
        command.setErrorMessage(error);
        command.setRequestIp(resolveIp(http));
        auditLogService.record(command);
    }

    /**
     * 序列化对象。
     *
     * @param value 对象
     * @return JSON 字符串
     * @author 余炘洋
     * @date 2026-01-26
     */
    private String serialize(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return null;
        }
    }

    /**
     * 解析 IP。
     *
     * @param http 请求
     * @return IP
     * @author 余炘洋
     * @date 2026-01-26
     */
    private String resolveIp(HttpServletRequest http) {
        if (http == null) {
            return null;
        }
        return http.getRemoteAddr();
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
