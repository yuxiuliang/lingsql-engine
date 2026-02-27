package com.lingsql.tenant.service;

import com.lingsql.config.LingSqlProperties;
import com.lingsql.tenant.entity.TenantInfo;
import com.lingsql.tenant.model.TenantLifecycleSnapshot;
import com.lingsql.tenant.model.TenantLifecycleState;
import com.lingsql.tenant.model.TenantRiskLevel;
import com.lingsql.tenant.model.TenantRiskSnapshot;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TenantLifecycleServiceTest {

    @Test
    void shouldMarkExpiringSoon() {
        LingSqlProperties properties = new LingSqlProperties();
        properties.getTenantLifecycle().setExpiringSoonDays(7);
        properties.getTenantLifecycle().setGracePeriod(Duration.ofDays(7));
        TenantLifecycleService service = new TenantLifecycleService(properties);
        TenantInfo tenant = buildTenant(1, LocalDateTime.of(2026, 2, 10, 0, 0));

        TenantLifecycleSnapshot snapshot = service.evaluate(tenant, LocalDateTime.of(2026, 2, 5, 0, 0));

        assertEquals(TenantLifecycleState.EXPIRING_SOON, snapshot.state());
        assertEquals(5L, snapshot.daysToExpire());
        assertTrue(snapshot.accessAllowed());
    }

    @Test
    void shouldMarkGracePeriodAfterExpire() {
        LingSqlProperties properties = new LingSqlProperties();
        properties.getTenantLifecycle().setGracePeriod(Duration.ofDays(7));
        TenantLifecycleService service = new TenantLifecycleService(properties);
        TenantInfo tenant = buildTenant(1, LocalDateTime.of(2026, 2, 10, 0, 0));

        TenantLifecycleSnapshot snapshot = service.evaluate(tenant, LocalDateTime.of(2026, 2, 12, 0, 0));

        assertEquals(TenantLifecycleState.GRACE_PERIOD, snapshot.state());
        assertEquals(-2L, snapshot.daysToExpire());
        assertEquals(5L, snapshot.graceDaysRemaining());
    }

    @Test
    void shouldMarkSuspendedWhenStatusInactiveAndBeyondGrace() {
        LingSqlProperties properties = new LingSqlProperties();
        properties.getTenantLifecycle().setGracePeriod(Duration.ofDays(7));
        TenantLifecycleService service = new TenantLifecycleService(properties);
        TenantInfo tenant = buildTenant(0, LocalDateTime.of(2026, 2, 10, 0, 0));

        TenantLifecycleSnapshot snapshot = service.evaluate(tenant, LocalDateTime.of(2026, 2, 20, 0, 0));

        assertEquals(TenantLifecycleState.SUSPENDED, snapshot.state());
        assertFalse(snapshot.accessAllowed());
    }

    @Test
    void shouldApplyTrialDefaultExpireTime() {
        LingSqlProperties properties = new LingSqlProperties();
        properties.getTenantLifecycle().setTrialDefaultDays(14);
        TenantLifecycleService service = new TenantLifecycleService(properties);
        TenantInfo tenant = new TenantInfo();
        tenant.setStatus(1);
        tenant.setTenantType("TRIAL");

        service.applyDefaultExpireTimeForTrial(tenant);

        assertNotNull(tenant.getExpireTime());
        assertTrue(tenant.getExpireTime().isAfter(LocalDateTime.now().plusDays(13)));
    }

    @Test
    void shouldMarkCriticalRiskWhenSuspended() {
        LingSqlProperties properties = new LingSqlProperties();
        properties.getTenantLifecycle().setGracePeriod(Duration.ofDays(7));
        TenantLifecycleService service = new TenantLifecycleService(properties);
        TenantInfo tenant = buildTenant(0, LocalDateTime.of(2026, 2, 10, 0, 0));
        tenant.setTenantType("TRIAL");
        tenant.setServiceLevel("FREE");
        tenant.setQuotaBalance(0L);

        TenantRiskSnapshot snapshot = service.evaluateRisk(tenant, LocalDateTime.of(2026, 2, 20, 0, 0));

        assertTrue(snapshot.score() >= 90);
        assertEquals(TenantRiskLevel.CRITICAL, snapshot.level());
    }

    @Test
    void shouldMarkLowRiskForStableTenant() {
        LingSqlProperties properties = new LingSqlProperties();
        properties.getTenantLifecycle().setExpiringSoonDays(7);
        TenantLifecycleService service = new TenantLifecycleService(properties);
        TenantInfo tenant = buildTenant(1, LocalDateTime.of(2026, 3, 20, 0, 0));
        tenant.setQuotaBalance(5000L);
        tenant.setServiceLevel("PRO");
        tenant.setContactEmail("ops@example.com");

        TenantRiskSnapshot snapshot = service.evaluateRisk(tenant, LocalDateTime.of(2026, 2, 20, 0, 0));

        assertTrue(snapshot.score() < 50);
        assertEquals(TenantRiskLevel.LOW, snapshot.level());
    }

    private TenantInfo buildTenant(Integer status, LocalDateTime expireTime) {
        TenantInfo tenant = new TenantInfo();
        tenant.setStatus(status);
        tenant.setExpireTime(expireTime);
        return tenant;
    }
}
