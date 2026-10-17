package com.lingsql.web;

import com.lingsql.agent.entity.AgentPluginGrant;
import com.lingsql.agent.model.AgentPluginGrantCommand;
import com.lingsql.agent.model.AgentPluginView;
import com.lingsql.agent.plugin.AgentPluginAccess;
import com.lingsql.agent.plugin.AgentPluginCost;
import com.lingsql.agent.plugin.AgentPluginDescriptor;
import com.lingsql.agent.plugin.AgentPluginRegistry;
import com.lingsql.agent.service.AgentPluginGrantService;
import com.lingsql.audit.service.AuditOperationExecutor;
import com.lingsql.common.exception.BizException;
import com.lingsql.common.result.ApiResponse;
import com.lingsql.rbac.security.RbacUserSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 插件治理管理接口测试：系统管理员专属，读取元数据与维护租户授权。
 *
 * @author 余炘洋
 * @date 2026-10-17
 */
class AgentPluginAdminControllerTest {

    private static final AgentPluginDescriptor CHART_DESCRIPTOR = new AgentPluginDescriptor(
            "chart_mapper", "1.0.0", AgentPluginAccess.ORCHESTRATED, AgentPluginCost.LOW, true, "图表映射");
    private static final AgentPluginDescriptor QUERY_DESCRIPTOR = new AgentPluginDescriptor(
            "secure_query", "1.0.0", AgentPluginAccess.SYSTEM_ONLY, AgentPluginCost.HIGH, false, "安全查询");

    private AgentPluginRegistry pluginRegistry;
    private AgentPluginGrantService pluginGrantService;
    private AuditOperationExecutor auditOperationExecutor;
    private AgentPluginAdminController controller;

    @BeforeEach
    void setUp() {
        pluginRegistry = Mockito.mock(AgentPluginRegistry.class);
        pluginGrantService = Mockito.mock(AgentPluginGrantService.class);
        auditOperationExecutor = Mockito.mock(AuditOperationExecutor.class);
        controller = new AgentPluginAdminController(pluginRegistry, pluginGrantService, auditOperationExecutor);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void listPluginsShouldExposeRegistryMetadataToSysAdmin() {
        bindSession(List.of("SUPER_ADMIN"));
        when(pluginRegistry.descriptors()).thenReturn(List.of(QUERY_DESCRIPTOR, CHART_DESCRIPTOR));

        ApiResponse<List<AgentPluginView>> response = controller.listPlugins();

        assertEquals(200, response.getCode());
        List<AgentPluginView> views = response.getData();
        assertEquals(2, views.size());
        assertEquals("chart_mapper", views.getFirst().pluginId());
        assertEquals("ORCHESTRATED", views.getFirst().access());
        assertEquals("LOW", views.getFirst().costLevel());
        assertTrue(views.getFirst().grantable());
        assertNull(views.getFirst().granted());
        assertFalse(views.get(1).grantable());
        assertNull(views.get(1).granted());
        verifyNoInteractions(pluginGrantService);
    }

    @Test
    void listPluginsShouldRejectTenantAdmin() {
        bindSession(List.of("TENANT_ADMIN"));

        assertThrows(AccessDeniedException.class, () -> controller.listPlugins());
    }

    @Test
    void listTenantPluginsShouldMergeTenantGrants() {
        bindSession(List.of("SUPER_ADMIN"));
        when(pluginRegistry.descriptors()).thenReturn(List.of(QUERY_DESCRIPTOR, CHART_DESCRIPTOR));
        when(pluginGrantService.listByTenant("tenant-a")).thenReturn(List.of(grant("tenant-a", "chart_mapper", true)));

        ApiResponse<List<AgentPluginView>> response = controller.listTenantPlugins("tenant-a");

        List<AgentPluginView> views = response.getData();
        assertEquals(Boolean.TRUE, views.getFirst().granted());
        assertNull(views.get(1).granted());
        assertFalse(views.get(1).grantable());
    }

    @Test
    void updateTenantPluginShouldDelegateByAuditExecutor() {
        bindSession(List.of("SUPER_ADMIN"));
        when(pluginRegistry.descriptors()).thenReturn(List.of(QUERY_DESCRIPTOR, CHART_DESCRIPTOR));
        when(pluginGrantService.listByTenant("tenant-a")).thenReturn(List.of(grant("tenant-a", "chart_mapper", true)));
        mockAuditExecute();

        ApiResponse<AgentPluginView> response = controller.updateTenantPlugin(
                "tenant-a", "chart_mapper", new AgentPluginGrantCommand(true), new MockHttpServletRequest());

        assertEquals(200, response.getCode());
        assertEquals(Boolean.TRUE, response.getData().granted());
        verify(pluginGrantService).grant("tenant-a", "chart_mapper", true);
        verify(auditOperationExecutor).execute(
                eq("TENANT_PLUGIN_GRANT_UPDATE"),
                eq("TENANT_PLUGIN_GRANT"),
                any(),
                any(),
                any(),
                any()
        );
    }

    @Test
    void updateTenantPluginShouldRejectMissingGrantedFlag() {
        bindSession(List.of("SUPER_ADMIN"));

        assertThrows(BizException.class, () -> controller.updateTenantPlugin(
                "tenant-a", "chart_mapper", new AgentPluginGrantCommand(null), new MockHttpServletRequest()));
        verifyNoInteractions(auditOperationExecutor);
    }

    @Test
    void updateTenantPluginShouldRejectTenantAdmin() {
        bindSession(List.of("TENANT_ADMIN"));

        assertThrows(AccessDeniedException.class, () -> controller.updateTenantPlugin(
                "tenant-a", "chart_mapper", new AgentPluginGrantCommand(true), new MockHttpServletRequest()));
        verifyNoInteractions(auditOperationExecutor);
    }

    private AgentPluginGrant grant(String tenantId, String pluginId, boolean granted) {
        AgentPluginGrant grant = new AgentPluginGrant();
        grant.setTenantId(tenantId);
        grant.setPluginId(pluginId);
        grant.setGranted(granted);
        return grant;
    }

    private void mockAuditExecute() {
        when(auditOperationExecutor.execute(anyString(), anyString(), any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    Supplier<?> supplier = invocation.getArgument(4);
                    return ApiResponse.success(supplier.get());
                });
    }

    private void bindSession(List<String> roles) {
        RbacUserSession session = new RbacUserSession();
        session.setUserId(1L);
        session.setTenantId("system");
        session.setRoles(roles);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(session, "token", List.of())
        );
    }
}
