package com.lingsql.tenant.service;

import com.lingsql.billing.BillingBalanceService;
import com.lingsql.billing.repository.BillingPlanRepository;
import com.lingsql.datasource.repository.TenantDbConfigRepository;
import com.lingsql.executor.SqlTaskTracker;
import com.lingsql.ops.OpsAlertService;
import com.lingsql.ops.model.AlertDispatchChannelResult;
import com.lingsql.ops.model.AlertDispatchResult;
import com.lingsql.organization.service.OrganizationAdminService;
import com.lingsql.rbac.repository.RbacPermissionRepository;
import com.lingsql.rbac.repository.RbacRoleDataScopeRepository;
import com.lingsql.rbac.repository.RbacRolePermissionRepository;
import com.lingsql.rbac.repository.RbacRoleRepository;
import com.lingsql.rbac.repository.RbacTenantPermissionRepository;
import com.lingsql.rbac.repository.RbacUserRepository;
import com.lingsql.tenant.entity.TenantInfo;
import com.lingsql.tenant.model.TenantNotificationChannelHealthView;
import com.lingsql.tenant.model.TenantNotificationChannelTestRequest;
import com.lingsql.tenant.model.TenantNotificationChannelTestView;
import com.lingsql.tenant.repository.TenantRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

class TenantAdminServiceNotificationChannelTest {

    @Test
    void shouldSendNotificationChannelTestAndMapStructuredResult() {
        OpsAlertService opsAlertService = Mockito.mock(OpsAlertService.class);
        TenantAdminService service = buildService(opsAlertService);
        when(opsAlertService.sendAlertToChannelsWithResult(anyString(), anyString(), anyMap(), anyList()))
                .thenReturn(AlertDispatchResult.completed(false, List.of(
                        AlertDispatchChannelResult.success("dingtalk", 200),
                        AlertDispatchChannelResult.failure("wecom", 500, "internal error")
                )));

        TenantNotificationChannelTestRequest request = new TenantNotificationChannelTestRequest();
        request.setTenantId("TENANT_A");
        request.setTenantName("演示租户");
        request.setChannels(List.of("dingtalk", "wecom"));

        TenantNotificationChannelTestView view = service.testNotificationChannels(request);

        assertEquals("TENANT_A", view.getTenantId());
        assertEquals("演示租户", view.getTenantName());
        assertEquals(List.of("dingtalk", "wecom"), view.getRequestedChannels());
        assertFalse(Boolean.TRUE.equals(view.getSuccess()));
        assertTrue(view.getSummary().contains("钉钉=成功(HTTP 200)"));
        assertTrue(view.getSummary().contains("企微=失败(HTTP 500)：internal error"));
        assertEquals(2, view.getChannelResults().size());
    }

    @Test
    void shouldBuildNotificationChannelHealthView() {
        TenantRepository tenantRepository = Mockito.mock(TenantRepository.class);
        OpsAlertService opsAlertService = Mockito.mock(OpsAlertService.class);
        TenantAdminService service = buildService(tenantRepository, opsAlertService);

        TenantInfo healthyTenant = new TenantInfo();
        healthyTenant.setTenantId("TENANT_OK");
        healthyTenant.setTenantName("健康租户");
        healthyTenant.setNotificationChannels("webhook");
        TenantInfo riskyTenant = new TenantInfo();
        riskyTenant.setTenantId("TENANT_RISK");
        riskyTenant.setTenantName("风险租户");
        riskyTenant.setNotificationChannels("webhook,dingtalk");

        when(tenantRepository.findByIsDeleted(0)).thenReturn(List.of(healthyTenant, riskyTenant));
        when(opsAlertService.isAlertEnabled()).thenReturn(true);
        when(opsAlertService.getConfiguredChannels()).thenReturn(List.of("webhook"));
        when(opsAlertService.getUnconfiguredChannels()).thenReturn(List.of("dingtalk", "wecom"));

        TenantNotificationChannelHealthView view = service.notificationChannelHealth();

        assertTrue(Boolean.TRUE.equals(view.getAlertEnabled()));
        assertEquals(2, view.getTenantUsingNotificationChannels());
        assertEquals(1, view.getTenantIssueCount());
        assertEquals(1, view.getIssues().size());
        assertEquals("TENANT_RISK", view.getIssues().getFirst().getTenantId());
        assertTrue(view.getIssues().getFirst().getReason().contains("dingtalk"));
    }

    private TenantAdminService buildService(OpsAlertService opsAlertService) {
        return buildService(Mockito.mock(TenantRepository.class), opsAlertService);
    }

    private TenantAdminService buildService(TenantRepository tenantRepository, OpsAlertService opsAlertService) {
        return new TenantAdminService(
                tenantRepository,
                Mockito.mock(TenantDbConfigRepository.class),
                Mockito.mock(RbacPermissionRepository.class),
                Mockito.mock(RbacTenantPermissionRepository.class),
                Mockito.mock(RbacRoleRepository.class),
                Mockito.mock(RbacRolePermissionRepository.class),
                Mockito.mock(RbacRoleDataScopeRepository.class),
                Mockito.mock(RbacUserRepository.class),
                Mockito.mock(SqlTaskTracker.class),
                Mockito.mock(BillingBalanceService.class),
                Mockito.mock(BillingPlanRepository.class),
                Mockito.mock(TenantLifecycleService.class),
                Mockito.mock(OrganizationAdminService.class),
                opsAlertService,
                Mockito.mock(com.lingsql.rbac.service.UserAdminService.class),
                Mockito.mock(com.lingsql.rbac.repository.RbacUserRoleRepository.class),
                Mockito.mock(com.lingsql.datasource.TenantDataSourceConfigService.class)
        );
    }
}
