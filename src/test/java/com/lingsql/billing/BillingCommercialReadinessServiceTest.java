package com.lingsql.billing;

import com.lingsql.billing.entity.BillingPlan;
import com.lingsql.billing.model.BillingCommercialReadinessView;
import com.lingsql.billing.repository.BillingLedgerEventRepository;
import com.lingsql.billing.repository.BillingPlanRepository;
import com.lingsql.billing.repository.BillingReconcileLogRepository;
import com.lingsql.config.LingSqlProperties;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class BillingCommercialReadinessServiceTest {

    @Test
    void shouldMarkReadyWhenAllChecksPassed() {
        BillingLedgerEventRepository ledgerRepository = Mockito.mock(BillingLedgerEventRepository.class);
        BillingReconcileLogRepository reconcileRepository = Mockito.mock(BillingReconcileLogRepository.class);
        BillingPlanRepository planRepository = Mockito.mock(BillingPlanRepository.class);
        LingSqlProperties properties = new LingSqlProperties();
        properties.getBilling().setReadinessWindowHours(24);
        properties.getBilling().setReadinessFailedRateThreshold(0.05D);
        properties.getBilling().setReadinessMaxOpenReconcile(1);
        BillingCommercialReadinessService service = new BillingCommercialReadinessService(
                ledgerRepository,
                reconcileRepository,
                planRepository,
                properties
        );

        when(ledgerRepository.countByCreateTimeAfter(any(LocalDateTime.class))).thenReturn(200L);
        when(ledgerRepository.countByCreateTimeAfterAndStatus(any(LocalDateTime.class), eq(2))).thenReturn(2L);
        when(reconcileRepository.countByStatus(0)).thenReturn(1L);
        when(planRepository.findByIsDeleted(0)).thenReturn(List.of(buildPlan(1, 1000L)));

        BillingCommercialReadinessView view = service.snapshot(null);

        assertTrue(view.isReady());
        assertEquals(24, view.getWindowHours());
        assertEquals(200L, view.getLedgerTotal());
        assertEquals(2L, view.getLedgerFailed());
        assertEquals(1L, view.getOpenReconcileCount());
        assertEquals(6, view.getChecks().size());
    }

    @Test
    void shouldMarkNotReadyWhenRiskItemsExist() {
        BillingLedgerEventRepository ledgerRepository = Mockito.mock(BillingLedgerEventRepository.class);
        BillingReconcileLogRepository reconcileRepository = Mockito.mock(BillingReconcileLogRepository.class);
        BillingPlanRepository planRepository = Mockito.mock(BillingPlanRepository.class);
        LingSqlProperties properties = new LingSqlProperties();
        properties.getBilling().setReadinessWindowHours(12);
        properties.getBilling().setReadinessFailedRateThreshold(0.01D);
        properties.getBilling().setReadinessMaxOpenReconcile(0);
        BillingCommercialReadinessService service = new BillingCommercialReadinessService(
                ledgerRepository,
                reconcileRepository,
                planRepository,
                properties
        );

        when(ledgerRepository.countByCreateTimeAfter(any(LocalDateTime.class))).thenReturn(100L);
        when(ledgerRepository.countByCreateTimeAfterAndStatus(any(LocalDateTime.class), eq(2))).thenReturn(20L);
        when(reconcileRepository.countByStatus(0)).thenReturn(3L);
        when(planRepository.findByIsDeleted(0)).thenReturn(List.of(buildPlan(0, -1L), buildPlan(0, null)));

        BillingCommercialReadinessView view = service.snapshot(6);

        assertFalse(view.isReady());
        assertEquals(6, view.getWindowHours());
        assertEquals(20L, view.getLedgerFailed());
        assertEquals(3L, view.getOpenReconcileCount());
        assertEquals(0L, view.getActivePlanCount());
        assertEquals(2L, view.getInvalidPlanCount());
    }

    private BillingPlan buildPlan(int status, Long defaultBalance) {
        BillingPlan plan = new BillingPlan();
        plan.setStatus(status);
        plan.setDefaultQuotaBalance(defaultBalance);
        return plan;
    }
}
