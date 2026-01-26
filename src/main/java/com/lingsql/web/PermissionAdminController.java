package com.lingsql.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingsql.audit.model.AuditLogCommand;
import com.lingsql.audit.service.AuditLogService;
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
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;

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
        try {
            tenantPermissionService.updateTenantPermissions(tenantId, request == null ? null : request.getPermIds());
            recordAudit(session, "TENANT_PERMISSION_UPDATE", tenantId, request, true, null, http);
            return ApiResponse.success("权限池已更新");
        } catch (Exception ex) {
            recordAudit(session, "TENANT_PERMISSION_UPDATE", tenantId, request, false, ex.getMessage(), http);
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
     * @param session  会话
     * @param action   动作
     * @param resource 资源
     * @param request  请求体
     * @param success  是否成功
     * @param error    错误信息
     * @param http     请求
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
}
