package com.lingsql.billing;

import com.lingsql.billing.entity.BillingPlan;
import com.lingsql.billing.model.BillingPlanCreateRequest;
import com.lingsql.billing.model.BillingPlanUpdateRequest;
import com.lingsql.billing.repository.BillingPlanRepository;
import com.lingsql.common.exception.BizException;
import com.lingsql.tenant.repository.TenantRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class BillingPlanServiceTest {

    @Test
    void shouldDefaultQuotaBalanceToZeroWhenCreateRequestIsNull() {
        BillingPlanRepository planRepository = Mockito.mock(BillingPlanRepository.class);
        TenantRepository tenantRepository = Mockito.mock(TenantRepository.class);
        BillingPlanService service = new BillingPlanService(planRepository, tenantRepository);

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
        BillingPlanService service = new BillingPlanService(planRepository, tenantRepository);

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
        BillingPlanService service = new BillingPlanService(planRepository, tenantRepository);

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
}
