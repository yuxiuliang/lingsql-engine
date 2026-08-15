package com.lingsql.tenant.service;

import com.lingsql.billing.BillingBalanceService;
import com.lingsql.billing.repository.BillingPlanRepository;
import com.lingsql.datasource.model.TenantDbConfig;
import com.lingsql.datasource.repository.TenantDbConfigRepository;
import com.lingsql.executor.SqlTaskTracker;
import com.lingsql.ops.OpsAlertService;
import com.lingsql.organization.service.OrganizationAdminService;
import com.lingsql.rbac.repository.RbacPermissionRepository;
import com.lingsql.rbac.repository.RbacRoleDataScopeRepository;
import com.lingsql.rbac.repository.RbacRolePermissionRepository;
import com.lingsql.rbac.repository.RbacRoleRepository;
import com.lingsql.rbac.repository.RbacTenantPermissionRepository;
import com.lingsql.rbac.repository.RbacUserRepository;
import com.lingsql.tenant.entity.TenantInfo;
import com.lingsql.tenant.model.TenantLifecycleExtendRequest;
import com.lingsql.tenant.model.TenantLifecycleOpsTaskView;
import com.lingsql.tenant.model.TenantLifecycleSnapshot;
import com.lingsql.tenant.model.TenantLifecycleState;
import com.lingsql.tenant.model.TenantRiskLevel;
import com.lingsql.tenant.model.TenantRiskSnapshot;
import com.lingsql.tenant.model.TenantLifecycleSummaryView;
import com.lingsql.tenant.model.TenantLifecycleWorkbenchView;
import com.lingsql.tenant.model.TenantView;
import com.lingsql.tenant.repository.TenantRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TenantAdminServiceLifecycleOpsTest {

    @Test
    void shouldExtendTenantLifecycleAndActivate() {
        TenantRepository tenantRepository = Mockito.mock(TenantRepository.class);
        TenantDbConfigRepository configRepository = Mockito.mock(TenantDbConfigRepository.class);
        TenantLifecycleService lifecycleService = Mockito.mock(TenantLifecycleService.class);
        TenantAdminService service = buildService(tenantRepository, configRepository, lifecycleService);
        TenantInfo tenant = new TenantInfo();
        tenant.setId(1L);
        tenant.setTenantId("t-001");
        tenant.setTenantName("租户A");
        tenant.setStatus(0);
        tenant.setExpireTime(LocalDateTime.now().minusDays(1));
        TenantDbConfig config = new TenantDbConfig();
        config.setTenantId("t-001");
        config.setStatus(0);

        when(tenantRepository.findByIdAndIsDeleted(1L, 0)).thenReturn(Optional.of(tenant));
        when(configRepository.findByTenantId("t-001")).thenReturn(Optional.of(config));
        when(tenantRepository.save(any(TenantInfo.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(lifecycleService.evaluate(any(TenantInfo.class)))
                .thenReturn(new TenantLifecycleSnapshot(TenantLifecycleState.ACTIVE, 30, 0));

        TenantLifecycleExtendRequest request = new TenantLifecycleExtendRequest();
        request.setExtendDays(30);
        request.setActivate(true);
        TenantView view = service.extendTenantLifecycle(1L, request);

        assertEquals(1, view.getStatus());
        assertTrue(view.getExpireTime().isAfter(LocalDateTime.now().plusDays(29)));
        verify(configRepository, times(1)).save(config);
    }

    @Test
    void shouldReactivateAndFallbackExpireTo30Days() {
        TenantRepository tenantRepository = Mockito.mock(TenantRepository.class);
        TenantDbConfigRepository configRepository = Mockito.mock(TenantDbConfigRepository.class);
        TenantLifecycleService lifecycleService = Mockito.mock(TenantLifecycleService.class);
        TenantAdminService service = buildService(tenantRepository, configRepository, lifecycleService);
        TenantInfo tenant = new TenantInfo();
        tenant.setId(2L);
        tenant.setTenantId("t-002");
        tenant.setTenantName("租户B");
        tenant.setStatus(0);
        tenant.setExpireTime(LocalDateTime.now().minusDays(5));

        when(tenantRepository.findByIdAndIsDeleted(2L, 0)).thenReturn(Optional.of(tenant));
        when(tenantRepository.save(any(TenantInfo.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(lifecycleService.evaluate(any(TenantInfo.class)))
                .thenReturn(new TenantLifecycleSnapshot(TenantLifecycleState.ACTIVE, 30, 0));

        TenantView view = service.reactivateTenant(2L);

        assertEquals(1, view.getStatus());
        assertTrue(view.getExpireTime().isAfter(LocalDateTime.now().plusDays(29)));
    }

    @Test
    void shouldAggregateLifecycleSummaryWithRiskStats() {
        TenantRepository tenantRepository = Mockito.mock(TenantRepository.class);
        TenantDbConfigRepository configRepository = Mockito.mock(TenantDbConfigRepository.class);
        TenantLifecycleService lifecycleService = Mockito.mock(TenantLifecycleService.class);
        TenantAdminService service = buildService(tenantRepository, configRepository, lifecycleService);
        TenantInfo active = new TenantInfo();
        active.setTenantId("t-001");
        active.setStatus(1);
        TenantInfo suspended = new TenantInfo();
        suspended.setTenantId("t-002");
        suspended.setStatus(0);

        when(tenantRepository.findByIsDeleted(0)).thenReturn(List.of(active, suspended));
        when(lifecycleService.evaluate(any(TenantInfo.class), any(LocalDateTime.class))).thenAnswer(invocation -> {
            TenantInfo tenant = invocation.getArgument(0);
            if ("t-001".equals(tenant.getTenantId())) {
                return new TenantLifecycleSnapshot(TenantLifecycleState.ACTIVE, 20, 0);
            }
            return new TenantLifecycleSnapshot(TenantLifecycleState.SUSPENDED, -10, 0);
        });
        when(lifecycleService.evaluateRisk(any(TenantInfo.class), any(LocalDateTime.class))).thenAnswer(invocation -> {
            TenantInfo tenant = invocation.getArgument(0);
            if ("t-001".equals(tenant.getTenantId())) {
                return new TenantRiskSnapshot(20, TenantRiskLevel.LOW, List.of("稳定"));
            }
            return new TenantRiskSnapshot(95, TenantRiskLevel.CRITICAL, List.of("已冻结"));
        });

        TenantLifecycleSummaryView summary = service.lifecycleSummary();

        assertEquals(2, summary.getTotal());
        assertEquals(1, summary.getActive());
        assertEquals(1, summary.getSuspended());
        assertEquals(1, summary.getAtRisk());
        assertEquals(1, summary.getCriticalRisk());
    }

    @Test
    void shouldBuildLifecycleWorkbenchWithPriority() {
        TenantRepository tenantRepository = Mockito.mock(TenantRepository.class);
        TenantDbConfigRepository configRepository = Mockito.mock(TenantDbConfigRepository.class);
        TenantLifecycleService lifecycleService = Mockito.mock(TenantLifecycleService.class);
        TenantAdminService service = buildService(tenantRepository, configRepository, lifecycleService);
        TenantInfo suspended = new TenantInfo();
        suspended.setTenantId("t-001");
        suspended.setTenantName("冻结租户");
        TenantInfo expiring = new TenantInfo();
        expiring.setTenantId("t-002");
        expiring.setTenantName("即将到期租户");

        when(tenantRepository.findByIsDeleted(0)).thenReturn(List.of(suspended, expiring));
        when(lifecycleService.evaluate(any(TenantInfo.class), any(LocalDateTime.class))).thenAnswer(invocation -> {
            TenantInfo tenant = invocation.getArgument(0);
            if ("t-001".equals(tenant.getTenantId())) {
                return new TenantLifecycleSnapshot(TenantLifecycleState.SUSPENDED, -5, 0);
            }
            return new TenantLifecycleSnapshot(TenantLifecycleState.EXPIRING_SOON, 3, 0);
        });
        when(lifecycleService.evaluateRisk(any(TenantInfo.class), any(LocalDateTime.class))).thenAnswer(invocation -> {
            TenantInfo tenant = invocation.getArgument(0);
            if ("t-001".equals(tenant.getTenantId())) {
                return new TenantRiskSnapshot(96, TenantRiskLevel.CRITICAL, List.of("已冻结"));
            }
            return new TenantRiskSnapshot(62, TenantRiskLevel.MEDIUM, List.of("即将到期"));
        });

        TenantLifecycleWorkbenchView workbench = service.lifecycleWorkbench(10);

        assertEquals(2, workbench.getTotalTasks());
        assertEquals(1, workbench.getP0Count());
        assertEquals(1, workbench.getP2Count());
        TenantLifecycleOpsTaskView first = workbench.getTasks().get(0);
        assertEquals("t-001", first.getTenantId());
        assertEquals("P0", first.getPriority());
    }

    private TenantAdminService buildService(TenantRepository tenantRepository,
                                            TenantDbConfigRepository configRepository,
                                            TenantLifecycleService lifecycleService) {
        return new TenantAdminService(
                tenantRepository,
                configRepository,
                Mockito.mock(RbacPermissionRepository.class),
                Mockito.mock(RbacTenantPermissionRepository.class),
                Mockito.mock(RbacRoleRepository.class),
                Mockito.mock(RbacRolePermissionRepository.class),
                Mockito.mock(RbacRoleDataScopeRepository.class),
                Mockito.mock(RbacUserRepository.class),
                Mockito.mock(SqlTaskTracker.class),
                Mockito.mock(BillingBalanceService.class),
                Mockito.mock(BillingPlanRepository.class),
                lifecycleService,
                Mockito.mock(OrganizationAdminService.class),
                Mockito.mock(OpsAlertService.class),
                Mockito.mock(com.lingsql.rbac.service.UserAdminService.class)
        );
    }
}
