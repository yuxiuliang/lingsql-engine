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

    private TenantAdminService buildService(OpsAlertService opsAlertService) {
        return new TenantAdminService(
                Mockito.mock(TenantRepository.class),
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
                opsAlertService
        );
    }
}
