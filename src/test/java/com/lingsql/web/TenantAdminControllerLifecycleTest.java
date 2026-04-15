package com.lingsql.web;

import com.lingsql.audit.service.AuditOperationExecutor;
import com.lingsql.common.result.ApiResponse;
import com.lingsql.rbac.security.RbacUserSession;
import com.lingsql.tenant.model.TenantLifecycleExtendRequest;
import com.lingsql.tenant.model.TenantLifecycleWorkbenchView;
import com.lingsql.tenant.model.TenantNotificationChannelFailureStatsView;
import com.lingsql.tenant.model.TenantNotificationChannelHealthView;
import com.lingsql.tenant.model.TenantNotificationChannelRecentTestView;
import com.lingsql.tenant.model.TenantNotificationChannelTestRequest;
import com.lingsql.tenant.model.TenantNotificationChannelTestView;
import com.lingsql.tenant.model.TenantView;
import com.lingsql.tenant.service.TenantAdminService;
import com.lingsql.tenant.service.TenantNotificationChannelHistoryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TenantAdminControllerLifecycleTest {

    private TenantAdminService tenantAdminService;
    private TenantNotificationChannelHistoryService tenantNotificationChannelHistoryService;
    private AuditOperationExecutor auditOperationExecutor;
    private TenantAdminController controller;

    @BeforeEach
    void setUp() {
        tenantAdminService = Mockito.mock(TenantAdminService.class);
        tenantNotificationChannelHistoryService = Mockito.mock(TenantNotificationChannelHistoryService.class);
        auditOperationExecutor = Mockito.mock(AuditOperationExecutor.class);
        controller = new TenantAdminController(tenantAdminService, tenantNotificationChannelHistoryService, auditOperationExecutor);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void extendShouldReturnForbiddenWhenNotSysAdmin() {
        bindSession(List.of("TENANT_ADMIN"));
        TenantLifecycleExtendRequest request = new TenantLifecycleExtendRequest();

        ApiResponse<TenantView> response = controller.extendTenantLifecycle(1L, request, new MockHttpServletRequest());

        assertEquals(403, response.getCode());
        assertEquals("无权限访问", response.getMessage());
        verifyNoInteractions(tenantAdminService);
        verifyNoInteractions(auditOperationExecutor);
    }

    @Test
    void extendShouldSucceedForSysAdmin() {
        bindSession(List.of("SYS_ADMIN"));
        TenantLifecycleExtendRequest request = new TenantLifecycleExtendRequest();
        request.setExtendDays(45);
        request.setActivate(true);
        TenantView view = new TenantView();
        view.setId(1L);
        view.setTenantId("t-001");
        when(tenantAdminService.extendTenantLifecycle(1L, request)).thenReturn(view);
        mockAuditExecute();

        ApiResponse<TenantView> response = controller.extendTenantLifecycle(1L, request, new MockHttpServletRequest());

        assertEquals(200, response.getCode());
        assertEquals("t-001", response.getData().getTenantId());
        verify(tenantAdminService, times(1)).extendTenantLifecycle(1L, request);
        verify(auditOperationExecutor, times(1)).execute(
                eq("TENANT_LIFECYCLE_EXTEND"),
                eq("TENANT"),
                eq(request),
                any(),
                any()
        );
    }

    @Test
    void reactivateShouldReturnForbiddenWhenNotSysAdmin() {
        bindSession(List.of("TENANT_ADMIN"));

        ApiResponse<TenantView> response = controller.reactivateTenant(2L, new MockHttpServletRequest());

        assertEquals(403, response.getCode());
        assertEquals("无权限访问", response.getMessage());
        verifyNoInteractions(tenantAdminService);
        verifyNoInteractions(auditOperationExecutor);
    }

    @Test
    void reactivateShouldSucceedForSysAdmin() {
        bindSession(List.of("SUPER_ADMIN"));
        TenantView view = new TenantView();
        view.setId(2L);
        view.setTenantId("t-002");
        when(tenantAdminService.reactivateTenant(2L)).thenReturn(view);
        mockAuditExecute();

        ApiResponse<TenantView> response = controller.reactivateTenant(2L, new MockHttpServletRequest());

        assertEquals(200, response.getCode());
        assertEquals("t-002", response.getData().getTenantId());
        verify(tenantAdminService, times(1)).reactivateTenant(2L);
        verify(auditOperationExecutor, times(1)).execute(
                eq("TENANT_LIFECYCLE_REACTIVATE"),
                eq("TENANT"),
                eq("id=2"),
                any(),
                any()
        );
    }

    @Test
    void workbenchShouldReturnForbiddenWhenNotSysAdmin() {
        bindSession(List.of("TENANT_ADMIN"));

        ApiResponse<TenantLifecycleWorkbenchView> response = controller.lifecycleWorkbench(20);

        assertEquals(403, response.getCode());
        assertEquals("无权限访问", response.getMessage());
        verifyNoInteractions(tenantAdminService);
    }

    @Test
    void workbenchShouldSucceedForSysAdmin() {
        bindSession(List.of("SYS_ADMIN"));
        TenantLifecycleWorkbenchView workbench = new TenantLifecycleWorkbenchView();
        workbench.setTotalTasks(2);
        when(tenantAdminService.lifecycleWorkbench(20)).thenReturn(workbench);

        ApiResponse<TenantLifecycleWorkbenchView> response = controller.lifecycleWorkbench(20);

        assertEquals(200, response.getCode());
        assertEquals(2, response.getData().getTotalTasks());
        verify(tenantAdminService, times(1)).lifecycleWorkbench(20);
    }

    @Test
    void notificationChannelHealthShouldReturnForbiddenWhenNotSysAdmin() {
        bindSession(List.of("TENANT_ADMIN"));

        ApiResponse<TenantNotificationChannelHealthView> response = controller.notificationChannelHealth();

        assertEquals(403, response.getCode());
        assertEquals("无权限访问", response.getMessage());
        verifyNoInteractions(tenantAdminService);
        verifyNoInteractions(tenantNotificationChannelHistoryService);
    }

    @Test
    void notificationChannelHealthShouldSucceedForSysAdmin() {
        bindSession(List.of("SYS_ADMIN"));
        TenantNotificationChannelHealthView view = new TenantNotificationChannelHealthView();
        view.setTenantIssueCount(1);
        when(tenantAdminService.notificationChannelHealth()).thenReturn(view);

        ApiResponse<TenantNotificationChannelHealthView> response = controller.notificationChannelHealth();

        assertEquals(200, response.getCode());
        assertEquals(1, response.getData().getTenantIssueCount());
        verify(tenantAdminService, times(1)).notificationChannelHealth();
    }

    @Test
    void recentNotificationChannelTestsShouldReturnForbiddenWhenNotSysAdmin() {
        bindSession(List.of("TENANT_ADMIN"));

        ApiResponse<List<TenantNotificationChannelRecentTestView>> response = controller.recentNotificationChannelTests(null, 10);

        assertEquals(403, response.getCode());
        assertEquals("无权限访问", response.getMessage());
        verifyNoInteractions(tenantNotificationChannelHistoryService);
    }

    @Test
    void recentNotificationChannelTestsShouldSucceedForSysAdmin() {
        bindSession(List.of("SYS_ADMIN"));
        TenantNotificationChannelRecentTestView view = new TenantNotificationChannelRecentTestView();
        view.setTenantId("t-001");
        when(tenantNotificationChannelHistoryService.listRecentTests("t-001", 5)).thenReturn(List.of(view));

        ApiResponse<List<TenantNotificationChannelRecentTestView>> response = controller.recentNotificationChannelTests("t-001", 5);

        assertEquals(200, response.getCode());
        assertEquals(1, response.getData().size());
        assertEquals("t-001", response.getData().getFirst().getTenantId());
        verify(tenantNotificationChannelHistoryService, times(1)).listRecentTests("t-001", 5);
    }

    @Test
    void notificationChannelFailureStatsShouldReturnForbiddenWhenNotSysAdmin() {
        bindSession(List.of("TENANT_ADMIN"));

        ApiResponse<TenantNotificationChannelFailureStatsView> response = controller.notificationChannelFailureStats(null, 100);

        assertEquals(403, response.getCode());
        assertEquals("无权限访问", response.getMessage());
        verifyNoInteractions(tenantNotificationChannelHistoryService);
    }

    @Test
    void notificationChannelFailureStatsShouldSucceedForSysAdmin() {
        bindSession(List.of("SYS_ADMIN"));
        TenantNotificationChannelFailureStatsView view = new TenantNotificationChannelFailureStatsView();
        view.setFailedTests(2);
        when(tenantNotificationChannelHistoryService.summarizeFailures("t-001", 100)).thenReturn(view);

        ApiResponse<TenantNotificationChannelFailureStatsView> response = controller.notificationChannelFailureStats("t-001", 100);

        assertEquals(200, response.getCode());
        assertEquals(2, response.getData().getFailedTests());
        verify(tenantNotificationChannelHistoryService, times(1)).summarizeFailures("t-001", 100);
    }

    @Test
    void notificationChannelTestShouldReturnForbiddenWhenNotSysAdmin() {
        bindSession(List.of("TENANT_ADMIN"));
        TenantNotificationChannelTestRequest request = new TenantNotificationChannelTestRequest();
        request.setChannels(List.of("webhook"));

        ApiResponse<TenantNotificationChannelTestView> response = controller.testNotificationChannels(request, new MockHttpServletRequest());

        assertEquals(403, response.getCode());
        assertEquals("无权限访问", response.getMessage());
        verifyNoInteractions(tenantAdminService);
        verifyNoInteractions(auditOperationExecutor);
    }

    @Test
    void notificationChannelTestShouldSucceedForSysAdmin() {
        bindSession(List.of("SYS_ADMIN"));
        TenantNotificationChannelTestRequest request = new TenantNotificationChannelTestRequest();
        request.setTenantId("t-001");
        request.setTenantName("演示租户");
        request.setChannels(List.of("webhook"));
        TenantNotificationChannelTestView view = new TenantNotificationChannelTestView();
        view.setTenantId("t-001");
        view.setSuccess(true);
        when(tenantAdminService.testNotificationChannels(request)).thenReturn(view);
        mockAuditExecute();

        ApiResponse<TenantNotificationChannelTestView> response = controller.testNotificationChannels(request, new MockHttpServletRequest());

        assertEquals(200, response.getCode());
        assertEquals("t-001", response.getData().getTenantId());
        verify(tenantAdminService, times(1)).testNotificationChannels(request);
        verify(auditOperationExecutor, times(1)).execute(
                eq("TENANT_NOTIFICATION_CHANNEL_TEST"),
                eq("TENANT"),
                eq(request),
                any(),
                any(),
                any()
        );
    }

    private void mockAuditExecute() {
        when(auditOperationExecutor.execute(anyString(), anyString(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    Supplier<?> supplier = invocation.getArgument(4);
                    return ApiResponse.success(supplier.get());
                });
        when(auditOperationExecutor.execute(anyString(), anyString(), any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    Supplier<?> supplier = invocation.getArgument(4);
                    return ApiResponse.success(supplier.get());
                });
    }

    private void bindSession(List<String> roles) {
        RbacUserSession session = new RbacUserSession();
        session.setUserId(1L);
        session.setTenantId("SYS");
        session.setRoles(roles);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(session, "token", List.of())
        );
    }
}
