package com.lingsql.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingsql.audit.model.AuditLogCommand;
import com.lingsql.audit.service.AuditLogService;
import com.lingsql.common.result.ApiResponse;
import com.lingsql.rbac.security.RbacSecurityUtils;
import com.lingsql.rbac.security.RbacUserSession;
import com.lingsql.tenant.model.TenantCreateRequest;
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
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;

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
     * 创建租户。
     *
     * @param request 创建请求
     * @return 创建结果
     * @author 余炘洋
     * @date 2026-01-26
     */
    @PostMapping
    @PreAuthorize("hasAuthority('ADMIN:TENANT:WRITE')")
    public ApiResponse<TenantView> createTenant(@RequestBody TenantCreateRequest request) {
        if (!isSysAdmin()) {
            return ApiResponse.forbidden("无权限访问");
        }
        try {
            TenantView view = tenantAdminService.createTenant(request);
            recordAudit("TENANT_CREATE", "TENANT", request, true, null);
            return ApiResponse.success(view);
        } catch (Exception ex) {
            recordAudit("TENANT_CREATE", "TENANT", request, false, ex.getMessage());
            return ApiResponse.error(ex.getMessage());
        }
    }

    /**
     * 更新租户。
     *
     * @param id      租户ID
     * @param request 更新请求
     * @return 更新结果
     * @author 余炘洋
     * @date 2026-01-26
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMIN:TENANT:WRITE')")
    public ApiResponse<TenantView> updateTenant(@PathVariable Long id, @RequestBody TenantUpdateRequest request) {
        if (!isSysAdmin()) {
            return ApiResponse.forbidden("无权限访问");
        }
        try {
            TenantView view = tenantAdminService.updateTenant(id, request);
            recordAudit("TENANT_UPDATE", "TENANT", request, true, null);
            return ApiResponse.success(view);
        } catch (Exception ex) {
            recordAudit("TENANT_UPDATE", "TENANT", request, false, ex.getMessage());
            return ApiResponse.error(ex.getMessage());
        }
    }

    /**
     * 删除租户。
     *
     * @param id 租户ID
     * @return 删除结果
     * @author 余炘洋
     * @date 2026-01-26
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMIN:TENANT:DELETE')")
    public ApiResponse<String> deleteTenant(@PathVariable Long id) {
        if (!isSysAdmin()) {
            return ApiResponse.forbidden("无权限访问");
        }
        try {
            tenantAdminService.deleteTenant(id);
            recordAudit("TENANT_DELETE", "TENANT", "id=" + id, true, null);
            return ApiResponse.success("租户已删除");
        } catch (Exception ex) {
            recordAudit("TENANT_DELETE", "TENANT", "id=" + id, false, ex.getMessage());
            return ApiResponse.error(ex.getMessage());
        }
    }

    /**
     * 记录审计日志。
     *
     * @param action   动作
     * @param resource 资源
     * @param request  请求体
     * @param success  是否成功
     * @param error    错误信息
     * @author 余炘洋
     * @date 2026-01-26
     */
    private void recordAudit(String action, String resource, Object request, boolean success, String error) {
        RbacUserSession session = RbacSecurityUtils.currentSession();
        if (session == null) {
            return;
        }
        AuditLogCommand command = new AuditLogCommand();
        command.setTenantId(session.getTenantId());
        command.setUserId(session.getUserId());
        command.setUsername(session.getUsername());
        command.setAction(action);
        command.setResource(resource);
        command.setDetail(serialize(request));
        command.setSuccess(success);
        command.setErrorMessage(error);
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
