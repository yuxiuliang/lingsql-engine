package com.lingsql.billing;

import com.lingsql.billing.entity.BillingPlan;
import com.lingsql.billing.model.BillingPlanCreateRequest;
import com.lingsql.billing.model.BillingPlanUpdateRequest;
import com.lingsql.billing.repository.BillingPlanRepository;
import com.lingsql.common.exception.BizException;
import com.lingsql.tenant.entity.TenantInfo;
import com.lingsql.tenant.repository.TenantRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BillingPlanServiceTest {

    @Test
    void shouldDefaultQuotaBalanceToZeroWhenCreateRequestIsNull() {
        BillingPlanRepository planRepository = Mockito.mock(BillingPlanRepository.class);
        TenantRepository tenantRepository = Mockito.mock(TenantRepository.class);
        BillingBalanceService billingBalanceService = Mockito.mock(BillingBalanceService.class);
        BillingPlanService service = new BillingPlanService(planRepository, tenantRepository, billingBalanceService);

        BillingPlanCreateRequest request = new BillingPlanCreateRequest();
        request.setPlanCode("starter");
        request.setPlanName("Starter");
        request.setStatus(1);
        request.setDefaultQuotaBalance(null);

        when(planRepository.existsByPlanCodeAndIsDeleted(eq("STARTER"), eq(0))).thenReturn(false);
        when(planRepository.existsByPlanNameAndIsDeleted(eq("Starter"), eq(0))).thenReturn(false);
        when(planRepository.save(any(BillingPlan.class))).thenAnswer(invocation -> {
            BillingPlan plan = invocation.getArgument(0);
            plan.setId(1L);
            return plan;
        });

        var view = service.createPlan(request);
        assertEquals(0L, view.getDefaultQuotaBalance());
    }

    @Test
    void shouldRejectNegativeQuotaBalanceWhenCreatePlan() {
        BillingPlanRepository planRepository = Mockito.mock(BillingPlanRepository.class);
        TenantRepository tenantRepository = Mockito.mock(TenantRepository.class);
        BillingBalanceService billingBalanceService = Mockito.mock(BillingBalanceService.class);
        BillingPlanService service = new BillingPlanService(planRepository, tenantRepository, billingBalanceService);

        BillingPlanCreateRequest request = new BillingPlanCreateRequest();
        request.setPlanCode("pro");
        request.setPlanName("Pro");
        request.setDefaultQuotaBalance(-1L);

        when(planRepository.existsByPlanCodeAndIsDeleted(eq("PRO"), eq(0))).thenReturn(false);
        when(planRepository.existsByPlanNameAndIsDeleted(eq("Pro"), eq(0))).thenReturn(false);

        assertThrows(BizException.class, () -> service.createPlan(request));
    }

    @Test
    void shouldRejectNegativeQuotaBalanceWhenUpdatePlan() {
        BillingPlanRepository planRepository = Mockito.mock(BillingPlanRepository.class);
        TenantRepository tenantRepository = Mockito.mock(TenantRepository.class);
        BillingBalanceService billingBalanceService = Mockito.mock(BillingBalanceService.class);
        BillingPlanService service = new BillingPlanService(planRepository, tenantRepository, billingBalanceService);

        BillingPlan existing = new BillingPlan();
        existing.setId(10L);
        existing.setPlanCode("PLAN001");
        existing.setPlanName("标准版");
        existing.setStatus(1);
        existing.setIsDeleted(0);

        when(planRepository.findByIdAndIsDeleted(eq(10L), eq(0))).thenReturn(Optional.of(existing));

        BillingPlanUpdateRequest request = new BillingPlanUpdateRequest();
        request.setDefaultQuotaBalance(-100L);

        assertThrows(BizException.class, () -> service.updatePlan(10L, request));
    }

    @Test
    void shouldSyncZeroBalanceTenantWhenPlanDefaultQuotaChanges() {
        BillingPlanRepository planRepository = Mockito.mock(BillingPlanRepository.class);
        TenantRepository tenantRepository = Mockito.mock(TenantRepository.class);
        BillingBalanceService billingBalanceService = Mockito.mock(BillingBalanceService.class);
        BillingPlanService service = new BillingPlanService(planRepository, tenantRepository, billingBalanceService);

        BillingPlan existing = new BillingPlan();
        existing.setId(10L);
        existing.setPlanCode("PLAN001");
        existing.setPlanName("标准版");
        existing.setStatus(1);
        existing.setIsDeleted(0);
        existing.setServiceLevel("FREE");
        existing.setTenantType("SMB");
        existing.setDefaultQuotaBalance(0L);

        TenantInfo tenant = new TenantInfo();
        tenant.setTenantId("t-001");
        tenant.setPlanName("标准版");
        tenant.setQuotaBalance(0L);
        tenant.setServiceLevel("FREE");
        tenant.setTenantType("SMB");

        when(planRepository.findByIdAndIsDeleted(eq(10L), eq(0))).thenReturn(Optional.of(existing));
        when(planRepository.save(any(BillingPlan.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(tenantRepository.findByPlanNameAndIsDeleted("标准版", 0)).thenReturn(List.of(tenant));
        when(tenantRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        BillingPlanUpdateRequest request = new BillingPlanUpdateRequest();
        request.setDefaultQuotaBalance(9999999L);

        service.updatePlan(10L, request);

        assertEquals(9999999L, tenant.getQuotaBalance());
        verify(billingBalanceService).resetBalance("t-001", 9999999L);
    }

    @Test
    void shouldKeepCustomizedTenantBalanceWhenPlanDefaultQuotaChanges() {
        BillingPlanRepository planRepository = Mockito.mock(BillingPlanRepository.class);
        TenantRepository tenantRepository = Mockito.mock(TenantRepository.class);
        BillingBalanceService billingBalanceService = Mockito.mock(BillingBalanceService.class);
        BillingPlanService service = new BillingPlanService(planRepository, tenantRepository, billingBalanceService);

        BillingPlan existing = new BillingPlan();
        existing.setId(11L);
        existing.setPlanCode("PLAN002");
        existing.setPlanName("企业版");
        existing.setStatus(1);
        existing.setIsDeleted(0);
        existing.setServiceLevel("FREE");
        existing.setTenantType("SMB");
        existing.setDefaultQuotaBalance(0L);

        TenantInfo tenant = new TenantInfo();
        tenant.setTenantId("t-002");
        tenant.setPlanName("企业版");
        tenant.setQuotaBalance(128L);
        tenant.setServiceLevel("FREE");
        tenant.setTenantType("SMB");

        when(planRepository.findByIdAndIsDeleted(eq(11L), eq(0))).thenReturn(Optional.of(existing));
        when(planRepository.save(any(BillingPlan.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(tenantRepository.findByPlanNameAndIsDeleted("企业版", 0)).thenReturn(List.of(tenant));

        BillingPlanUpdateRequest request = new BillingPlanUpdateRequest();
        request.setDefaultQuotaBalance(9999999L);

        service.updatePlan(11L, request);

        assertEquals(128L, tenant.getQuotaBalance());
        verify(billingBalanceService, never()).resetBalance(eq("t-002"), any(Long.class));
    }
}
