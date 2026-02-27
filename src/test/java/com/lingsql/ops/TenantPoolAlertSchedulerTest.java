package com.lingsql.ops;

import com.lingsql.config.LingSqlProperties;
import com.lingsql.datasource.DataSourceManager;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Duration;

import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TenantPoolAlertSchedulerTest {

    @Test
    void shouldSendAlertWhenUtilizationExceedsThreshold() {
        DataSourceManager manager = Mockito.mock(DataSourceManager.class);
        OpsAlertService alertService = Mockito.mock(OpsAlertService.class);
        LingSqlProperties properties = new LingSqlProperties();
        properties.getAlert().setEnabled(true);
        properties.getAlert().setTenantPoolUtilizationThreshold(0.8D);
        properties.getAlert().setCooldown(Duration.ofMinutes(5));
        when(manager.activeTenantPoolCount()).thenReturn(180);
        when(manager.maxTenantPoolSize()).thenReturn(200);
        when(manager.tenantPoolUtilization()).thenReturn(0.9D);
        when(alertService.sendAlert(anyString(), anyString(), anyMap())).thenReturn(true);
        TenantPoolAlertScheduler scheduler = new TenantPoolAlertScheduler(manager, alertService, properties);

        scheduler.checkTenantPoolPressure();
        scheduler.checkTenantPoolPressure();

        verify(alertService, times(1)).sendAlert(anyString(), anyString(), anyMap());
    }

    @Test
    void shouldNotSendAlertWhenUtilizationBelowThreshold() {
        DataSourceManager manager = Mockito.mock(DataSourceManager.class);
        OpsAlertService alertService = Mockito.mock(OpsAlertService.class);
        LingSqlProperties properties = new LingSqlProperties();
        properties.getAlert().setEnabled(true);
        properties.getAlert().setTenantPoolUtilizationThreshold(0.9D);
        when(manager.activeTenantPoolCount()).thenReturn(100);
        when(manager.maxTenantPoolSize()).thenReturn(200);
        when(manager.tenantPoolUtilization()).thenReturn(0.5D);
        TenantPoolAlertScheduler scheduler = new TenantPoolAlertScheduler(manager, alertService, properties);

        scheduler.checkTenantPoolPressure();

        verify(alertService, never()).sendAlert(anyString(), anyString(), anyMap());
    }
}

