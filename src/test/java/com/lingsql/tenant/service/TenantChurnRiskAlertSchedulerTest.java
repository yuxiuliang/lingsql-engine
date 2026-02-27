package com.lingsql.tenant.service;

import com.lingsql.config.LingSqlProperties;
import com.lingsql.ops.OpsAlertService;
import com.lingsql.tenant.entity.TenantInfo;
import com.lingsql.tenant.model.TenantLifecycleSnapshot;
import com.lingsql.tenant.model.TenantLifecycleState;
import com.lingsql.tenant.model.TenantRiskLevel;
import com.lingsql.tenant.model.TenantRiskSnapshot;
import com.lingsql.tenant.repository.TenantRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TenantChurnRiskAlertSchedulerTest {

    @Test
    void shouldSendRiskAlertWhenThresholdHit() {
        TenantRepository repository = Mockito.mock(TenantRepository.class);
        TenantLifecycleService lifecycleService = Mockito.mock(TenantLifecycleService.class);
        OpsAlertService alertService = Mockito.mock(OpsAlertService.class);
        LingSqlProperties properties = new LingSqlProperties();
        properties.getTenantLifecycle().setRiskAlertEnabled(true);
        properties.getTenantLifecycle().setRiskAlertThreshold(70);
        properties.getTenantLifecycle().setRiskAlertTopN(10);
        TenantChurnRiskAlertScheduler scheduler = new TenantChurnRiskAlertScheduler(
                repository,
                lifecycleService,
                alertService,
                properties
        );
        TenantInfo highRisk = new TenantInfo();
        highRisk.setTenantId("t-001");
        highRisk.setTenantName("高风险租户");
        TenantInfo lowRisk = new TenantInfo();
        lowRisk.setTenantId("t-002");
        lowRisk.setTenantName("低风险租户");

        when(lifecycleService.isLifecycleEnabled()).thenReturn(true);
        when(repository.findByIsDeleted(0)).thenReturn(List.of(highRisk, lowRisk));
        when(lifecycleService.evaluateRisk(any(TenantInfo.class), any(LocalDateTime.class))).thenAnswer(invocation -> {
            TenantInfo tenant = invocation.getArgument(0);
            if ("t-001".equals(tenant.getTenantId())) {
                return new TenantRiskSnapshot(92, TenantRiskLevel.CRITICAL, List.of("已冻结"));
            }
            return new TenantRiskSnapshot(30, TenantRiskLevel.LOW, List.of("状态稳定"));
        });
        when(lifecycleService.evaluate(any(TenantInfo.class), any(LocalDateTime.class))).thenReturn(
                new TenantLifecycleSnapshot(TenantLifecycleState.SUSPENDED, -5, 0)
        );
        when(alertService.sendAlert(anyString(), anyString(), anyMap())).thenReturn(true);

        scheduler.alertHighRiskTenants();

        verify(alertService, times(1)).sendAlert(anyString(), anyString(), anyMap());
    }

    @Test
    void shouldSkipWhenRiskAlertDisabled() {
        TenantRepository repository = Mockito.mock(TenantRepository.class);
        TenantLifecycleService lifecycleService = Mockito.mock(TenantLifecycleService.class);
        OpsAlertService alertService = Mockito.mock(OpsAlertService.class);
        LingSqlProperties properties = new LingSqlProperties();
        properties.getTenantLifecycle().setRiskAlertEnabled(false);
        TenantChurnRiskAlertScheduler scheduler = new TenantChurnRiskAlertScheduler(
                repository,
                lifecycleService,
                alertService,
                properties
        );
        when(lifecycleService.isLifecycleEnabled()).thenReturn(true);

        scheduler.alertHighRiskTenants();

        verify(repository, never()).findByIsDeleted(any());
        verify(alertService, never()).sendAlert(anyString(), anyString(), anyMap());
    }
}

