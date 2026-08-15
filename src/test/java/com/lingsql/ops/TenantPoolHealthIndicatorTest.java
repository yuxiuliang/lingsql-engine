package com.lingsql.ops;

import com.lingsql.datasource.DataSourceManager;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

class TenantPoolHealthIndicatorTest {

    @Test
    void shouldReturnUpWhenPoolUsageIsNormal() {
        DataSourceManager dataSourceManager = Mockito.mock(DataSourceManager.class);
        when(dataSourceManager.activeTenantPoolCount()).thenReturn(12);
        when(dataSourceManager.maxTenantPoolSize()).thenReturn(200);
        when(dataSourceManager.tenantPoolUtilization()).thenReturn(0.06D);
        TenantPoolHealthIndicator indicator = new TenantPoolHealthIndicator(dataSourceManager);

        Health health = indicator.health();

        assertEquals(Status.UP, health.getStatus());
        assertEquals(12, health.getDetails().get("activePools"));
        assertEquals(200, health.getDetails().get("maxPools"));
        assertEquals(0.06D, health.getDetails().get("utilization"));
    }

    @Test
    void shouldReturnDownWhenActivePoolExceedsConfiguredMax() {
        DataSourceManager dataSourceManager = Mockito.mock(DataSourceManager.class);
        when(dataSourceManager.activeTenantPoolCount()).thenReturn(201);
        when(dataSourceManager.maxTenantPoolSize()).thenReturn(200);
        when(dataSourceManager.tenantPoolUtilization()).thenReturn(1D);
        TenantPoolHealthIndicator indicator = new TenantPoolHealthIndicator(dataSourceManager);

        Health health = indicator.health();

        assertEquals(Status.DOWN, health.getStatus());
        assertTrue(health.getDetails().containsKey("activePools"));
    }
}

