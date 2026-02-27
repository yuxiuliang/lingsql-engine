package com.lingsql.tenant.service;

import com.lingsql.config.LingSqlProperties;
import com.lingsql.ops.OpsAlertService;
import com.lingsql.tenant.entity.TenantInfo;
import com.lingsql.tenant.model.TenantLifecycleSnapshot;
import com.lingsql.tenant.model.TenantLifecycleState;
import com.lingsql.tenant.repository.TenantRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TenantLifecycleReminderSchedulerTest {

    @Test
    void shouldSendReminderOnlyOncePerDay() {
        TenantRepository repository = Mockito.mock(TenantRepository.class);
        TenantLifecycleService lifecycleService = Mockito.mock(TenantLifecycleService.class);
        OpsAlertService alertService = Mockito.mock(OpsAlertService.class);
        StringRedisTemplate redisTemplate = Mockito.mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = Mockito.mock(ValueOperations.class);
        LingSqlProperties properties = new LingSqlProperties();
        properties.getTenantLifecycle().setReminderEnabled(true);
        properties.getTenantLifecycle().setReminderDays(List.of(3, 1));
        properties.getTenantLifecycle().setReminderDedupeTtl(Duration.ofDays(2));
        TenantLifecycleReminderScheduler scheduler = new TenantLifecycleReminderScheduler(
                repository,
                lifecycleService,
                alertService,
                properties,
                redisTemplate
        );
        TenantInfo tenant = new TenantInfo();
        tenant.setTenantId("t-001");
        tenant.setTenantName("租户A");
        tenant.setStatus(1);
        tenant.setExpireTime(LocalDateTime.now().plusDays(3));

        when(lifecycleService.isLifecycleEnabled()).thenReturn(true);
        when(repository.findByIsDeletedAndStatusAndExpireTimeIsNotNull(0, 1)).thenReturn(List.of(tenant));
        when(lifecycleService.evaluate(any(TenantInfo.class), any(LocalDateTime.class)))
                .thenReturn(new TenantLifecycleSnapshot(TenantLifecycleState.EXPIRING_SOON, 3, 0));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenReturn(true)
                .thenReturn(false);
        when(alertService.sendAlert(anyString(), anyString(), anyMap())).thenReturn(true);

        scheduler.remindExpiringTenants();
        scheduler.remindExpiringTenants();

        verify(alertService, times(1)).sendAlert(anyString(), anyString(), anyMap());
    }

    @Test
    void shouldSkipWhenDayNotInReminderList() {
        TenantRepository repository = Mockito.mock(TenantRepository.class);
        TenantLifecycleService lifecycleService = Mockito.mock(TenantLifecycleService.class);
        OpsAlertService alertService = Mockito.mock(OpsAlertService.class);
        StringRedisTemplate redisTemplate = Mockito.mock(StringRedisTemplate.class);
        LingSqlProperties properties = new LingSqlProperties();
        properties.getTenantLifecycle().setReminderEnabled(true);
        properties.getTenantLifecycle().setReminderDays(List.of(7, 3, 1));
        TenantLifecycleReminderScheduler scheduler = new TenantLifecycleReminderScheduler(
                repository,
                lifecycleService,
                alertService,
                properties,
                redisTemplate
        );
        TenantInfo tenant = new TenantInfo();
        tenant.setTenantId("t-002");
        tenant.setStatus(1);
        tenant.setExpireTime(LocalDateTime.now().plusDays(5));

        when(lifecycleService.isLifecycleEnabled()).thenReturn(true);
        when(repository.findByIsDeletedAndStatusAndExpireTimeIsNotNull(0, 1)).thenReturn(List.of(tenant));
        when(lifecycleService.evaluate(any(TenantInfo.class), any(LocalDateTime.class)))
                .thenReturn(new TenantLifecycleSnapshot(TenantLifecycleState.EXPIRING_SOON, 5, 0));

        scheduler.remindExpiringTenants();

        verify(alertService, never()).sendAlert(anyString(), anyString(), anyMap());
    }

    @Test
    void shouldFallbackToMemoryDedupeWhenRedisUnavailable() {
        TenantRepository repository = Mockito.mock(TenantRepository.class);
        TenantLifecycleService lifecycleService = Mockito.mock(TenantLifecycleService.class);
        OpsAlertService alertService = Mockito.mock(OpsAlertService.class);
        StringRedisTemplate redisTemplate = Mockito.mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = Mockito.mock(ValueOperations.class);
        LingSqlProperties properties = new LingSqlProperties();
        properties.getTenantLifecycle().setReminderEnabled(true);
        properties.getTenantLifecycle().setReminderDays(List.of(1));
        TenantLifecycleReminderScheduler scheduler = new TenantLifecycleReminderScheduler(
                repository,
                lifecycleService,
                alertService,
                properties,
                redisTemplate
        );
        TenantInfo tenant = new TenantInfo();
        tenant.setTenantId("t-003");
        tenant.setTenantName("租户C");
        tenant.setStatus(1);
        tenant.setExpireTime(LocalDateTime.now().plusDays(1));

        when(lifecycleService.isLifecycleEnabled()).thenReturn(true);
        when(repository.findByIsDeletedAndStatusAndExpireTimeIsNotNull(0, 1)).thenReturn(List.of(tenant));
        when(lifecycleService.evaluate(any(TenantInfo.class), any(LocalDateTime.class)))
                .thenReturn(new TenantLifecycleSnapshot(TenantLifecycleState.EXPIRING_SOON, 1, 0));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenThrow(new RuntimeException("redis down"));
        when(alertService.sendAlert(anyString(), anyString(), anyMap())).thenReturn(true);

        scheduler.remindExpiringTenants();
        scheduler.remindExpiringTenants();

        verify(alertService, times(1)).sendAlert(anyString(), anyString(), anyMap());
    }
}
