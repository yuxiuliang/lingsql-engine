package com.lingsql.web;

import com.lingsql.agent.entity.AgentPluginGrant;
import com.lingsql.agent.model.AgentPluginGrantCommand;
import com.lingsql.agent.model.AgentPluginView;
import com.lingsql.agent.plugin.AgentPluginAccess;
import com.lingsql.agent.plugin.AgentPluginDescriptor;
import com.lingsql.agent.plugin.AgentPluginRegistry;
import com.lingsql.agent.service.AgentPluginGrantService;
import com.lingsql.audit.service.AuditOperationExecutor;
import com.lingsql.common.exception.BizException;
import com.lingsql.common.exception.ErrorCode;
import com.lingsql.common.result.ApiResponse;
import com.lingsql.rbac.security.RbacSecurityUtils;
import com.lingsql.rbac.security.SecureTenantResolver;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 插件治理管理入口：注册中心元数据查询与租户插件授权维护。
 *
 * <p>只读接口仅暴露稳定元数据，不返回插件实例；写入接口必须经审计执行器落账。
 * 系统插件由受控工作流固定调用，不参与租户授权。</p>
 *
 * @author 余炘洋
 * @date 2026-10-17
 */
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AgentPluginAdminController {

    private final AgentPluginRegistry pluginRegistry;
    private final AgentPluginGrantService pluginGrantService;
    private final AuditOperationExecutor auditOperationExecutor;

    @GetMapping("/plugins")
    @PreAuthorize("hasAuthority('ADMIN:PLUGIN:READ')")
    public ApiResponse<List<AgentPluginView>> listPlugins() {
        requireSysAdmin();
        return ApiResponse.success(buildPluginViews(null));
    }

    @GetMapping("/tenants/{tenantId}/plugins")
    @PreAuthorize("hasAuthority('ADMIN:PLUGIN:READ')")
    public ApiResponse<List<AgentPluginView>> listTenantPlugins(@PathVariable String tenantId) {
        requireSysAdmin();
        return ApiResponse.success(buildPluginViews(tenantId));
    }

    @PutMapping("/tenants/{tenantId}/plugins/{pluginId}")
    @PreAuthorize("hasAuthority('ADMIN:PLUGIN:WRITE')")
    public ApiResponse<AgentPluginView> updateTenantPlugin(
            @PathVariable String tenantId,
            @PathVariable String pluginId,
            @RequestBody(required = false) AgentPluginGrantCommand command,
            HttpServletRequest http
    ) {
        requireSysAdmin();
        if (command == null || command.granted() == null) {
            throw new BizException(ErrorCode.INVALID_PARAM, "插件授权状态 granted 不能为空");
        }
        Map<String, Object> auditDetail = Map.of(
                "tenantId", tenantId,
                "pluginId", pluginId,
                "granted", command.granted()
        );
        return auditOperationExecutor.execute(
                "TENANT_PLUGIN_GRANT_UPDATE",
                "TENANT_PLUGIN_GRANT",
                auditDetail,
                http,
                () -> {
                    pluginGrantService.grant(tenantId, pluginId, command.granted());
                    return requirePluginView(tenantId, pluginId);
                },
                result -> auditDetail
        );
    }

    private AgentPluginView requirePluginView(String tenantId, String pluginId) {
        return buildPluginViews(tenantId).stream()
                .filter(view -> view.pluginId().equals(pluginId == null ? null : pluginId.trim()))
                .findFirst()
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "Agent 插件不存在: " + pluginId));
    }

    private List<AgentPluginView> buildPluginViews(String tenantId) {
        boolean tenantScoped = StringUtils.hasText(tenantId);
        Map<String, Boolean> grants = tenantScoped ? loadGrants(tenantId) : Map.of();
        return pluginRegistry.descriptors().stream()
                .sorted(Comparator.comparing(AgentPluginDescriptor::id))
                .map(descriptor -> toView(descriptor, grants, tenantScoped))
                .toList();
    }

    private Map<String, Boolean> loadGrants(String tenantId) {
        Map<String, Boolean> grants = new HashMap<>();
        for (AgentPluginGrant grant : pluginGrantService.listByTenant(tenantId)) {
            if (grant != null && StringUtils.hasText(grant.getPluginId())) {
                grants.put(grant.getPluginId(), Boolean.TRUE.equals(grant.getGranted()));
            }
        }
        return grants;
    }

    private AgentPluginView toView(AgentPluginDescriptor descriptor, Map<String, Boolean> grants, boolean tenantScoped) {
        boolean grantable = descriptor.access() == AgentPluginAccess.ORCHESTRATED;
        Boolean granted = tenantScoped && grantable ? grants.getOrDefault(descriptor.id(), Boolean.FALSE) : null;
        return new AgentPluginView(
                descriptor.id(),
                descriptor.version(),
                descriptor.access().name(),
                descriptor.cost().name(),
                descriptor.idempotent(),
                grantable,
                granted,
                descriptor.description()
        );
    }

    private void requireSysAdmin() {
        if (!SecureTenantResolver.isSysAdmin(RbacSecurityUtils.currentSession())) {
            throw new AccessDeniedException("无权限访问");
        }
    }
}
