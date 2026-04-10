package com.lingsql.tenant.service;

import com.lingsql.billing.BillingBalanceService;
import com.lingsql.billing.entity.BillingPlan;
import com.lingsql.billing.repository.BillingPlanRepository;
import com.lingsql.datasource.repository.TenantDbConfigRepository;
import com.lingsql.executor.SqlTaskTracker;
import com.lingsql.rbac.repository.RbacPermissionRepository;
import com.lingsql.rbac.repository.RbacRoleDataScopeRepository;
import com.lingsql.rbac.repository.RbacRolePermissionRepository;
import com.lingsql.rbac.repository.RbacRoleRepository;
import com.lingsql.rbac.repository.RbacTenantPermissionRepository;
import com.lingsql.rbac.repository.RbacUserRepository;
import com.lingsql.tenant.entity.TenantInfo;
import com.lingsql.tenant.model.TenantLifecycleSnapshot;
import com.lingsql.tenant.model.TenantLifecycleState;
import com.lingsql.tenant.model.TenantRiskLevel;
import com.lingsql.tenant.model.TenantRiskSnapshot;
import com.lingsql.tenant.model.TenantUpdateRequest;
import com.lingsql.tenant.model.TenantView;
import com.lingsql.tenant.repository.TenantRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TenantAdminServicePlanBindingTest {

    @Test
    void shouldApplyPlanDefaultsWhenUpdateTenantWithZeroBalance() {
        TenantRepository tenantRepository = Mockito.mock(TenantRepository.class);
        BillingPlanRepository billingPlanRepository = Mockito.mock(BillingPlanRepository.class);
        BillingBalanceService billingBalanceService = Mockito.mock(BillingBalanceService.class);
        TenantLifecycleService lifecycleService = Mockito.mock(TenantLifecycleService.class);
        TenantAdminService service = buildService(tenantRepository, billingPlanRepository, billingBalanceService, lifecycleService);

        TenantInfo tenant = new TenantInfo();
        tenant.setId(1L);
        tenant.setTenantId("1");
        tenant.setTenantName("admin");
        tenant.setPlanName("测试");
        tenant.setQuotaBalance(0L);
        tenant.setServiceLevel("FREE");

        BillingPlan plan = new BillingPlan();
        plan.setPlanName("测试");
        plan.setDefaultQuotaBalance(9999999L);
        plan.setServiceLevel("PRO");
        plan.setTenantType("ENTERPRISE");

        when(tenantRepository.findByIdAndIsDeleted(1L, 0)).thenReturn(Optional.of(tenant));
        when(billingPlanRepository.findByPlanNameAndIsDeleted("测试", 0)).thenReturn(Optional.of(plan));
        when(tenantRepository.save(any(TenantInfo.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(lifecycleService.evaluate(any(TenantInfo.class)))
                .thenReturn(new TenantLifecycleSnapshot(TenantLifecycleState.ACTIVE, Long.MAX_VALUE, 0));
        when(lifecycleService.evaluateRisk(any(TenantInfo.class)))
                .thenReturn(new TenantRiskSnapshot(0, TenantRiskLevel.LOW, List.of("稳定")));

        TenantUpdateRequest request = new TenantUpdateRequest();
        request.setTenantName("admin");
        request.setPlanName("测试");

        TenantView view = service.updateTenant(1L, request);

        assertEquals(9999999L, view.getQuotaBalance());
        assertEquals("PRO", view.getServiceLevel());
        assertEquals("ENTERPRISE", view.getTenantType());
        verify(billingBalanceService).resetBalance("1", 9999999L);
    }

    private TenantAdminService buildService(TenantRepository tenantRepository,
                                            BillingPlanRepository billingPlanRepository,
                                            BillingBalanceService billingBalanceService,
                                            TenantLifecycleService lifecycleService) {
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
                billingBalanceService,
                billingPlanRepository,
                lifecycleService
        );
    }
}
