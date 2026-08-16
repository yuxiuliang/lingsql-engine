package com.lingsql.tenant.service;

import com.lingsql.datasource.model.TenantDbConfig;
import com.lingsql.datasource.repository.TenantDbConfigRepository;
import com.lingsql.rbac.authorization.IAuthorizationSessionLifecycle;
import com.lingsql.tenant.entity.TenantInfo;
import com.lingsql.tenant.model.TenantLifecycleSnapshot;
import com.lingsql.tenant.model.TenantLifecycleState;
import com.lingsql.tenant.repository.TenantRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Supplier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TenantLifecycleSchedulerTest {

    @Test
    void shouldAutoSuspendExpiredTenant() {
        TenantRepository repository = Mockito.mock(TenantRepository.class);
        TenantDbConfigRepository configRepository = Mockito.mock(TenantDbConfigRepository.class);
        TenantLifecycleService lifecycleService = Mockito.mock(TenantLifecycleService.class);
        TenantLifecycleScheduler scheduler = new TenantLifecycleScheduler(
                repository, configRepository, lifecycleService, authorizationLifecycle());
        TenantInfo expired = new TenantInfo();
        expired.setTenantId("t-expired");
        expired.setStatus(1);
        expired.setExpireTime(LocalDateTime.now().minusDays(10));
        TenantInfo active = new TenantInfo();
        active.setTenantId("t-active");
        active.setStatus(1);
        active.setExpireTime(LocalDateTime.now().plusDays(10));
        TenantDbConfig expiredConfig = new TenantDbConfig();
        expiredConfig.setTenantId("t-expired");
        expiredConfig.setStatus(1);

        when(lifecycleService.isLifecycleEnabled()).thenReturn(true);
        when(lifecycleService.isAutoSuspendEnabled()).thenReturn(true);
        when(repository.findByIsDeletedAndStatusAndExpireTimeIsNotNull(0, 1)).thenReturn(List.of(expired, active));
        when(configRepository.findByTenantId("t-expired")).thenReturn(java.util.Optional.of(expiredConfig));
        when(configRepository.findByTenantId("t-active")).thenReturn(java.util.Optional.empty());
        when(lifecycleService.evaluate(any(TenantInfo.class), any(LocalDateTime.class)))
                .thenReturn(new TenantLifecycleSnapshot(TenantLifecycleState.SUSPENDED, -10, 0))
                .thenReturn(new TenantLifecycleSnapshot(TenantLifecycleState.ACTIVE, 10, 0));

        scheduler.autoSuspendExpiredTenants();

        verify(repository, times(1)).save(expired);
        verify(repository, never()).save(active);
        verify(configRepository, times(1)).save(expiredConfig);
    }

    @Test
    void shouldSkipWhenAutoSuspendDisabled() {
        TenantRepository repository = Mockito.mock(TenantRepository.class);
        TenantDbConfigRepository configRepository = Mockito.mock(TenantDbConfigRepository.class);
        TenantLifecycleService lifecycleService = Mockito.mock(TenantLifecycleService.class);
        TenantLifecycleScheduler scheduler = new TenantLifecycleScheduler(
                repository, configRepository, lifecycleService, authorizationLifecycle());
        when(lifecycleService.isLifecycleEnabled()).thenReturn(true);
        when(lifecycleService.isAutoSuspendEnabled()).thenReturn(false);

        scheduler.autoSuspendExpiredTenants();

        verify(repository, never()).findByIsDeletedAndStatusAndExpireTimeIsNotNull(any(), any());
        verify(configRepository, never()).save(any());
    }

    private IAuthorizationSessionLifecycle authorizationLifecycle() {
        IAuthorizationSessionLifecycle lifecycle = Mockito.mock(IAuthorizationSessionLifecycle.class);
        Mockito.when(lifecycle.publish(Mockito.any(), Mockito.any()))
                .thenAnswer(invocation -> ((Supplier<?>) invocation.getArgument(1)).get());
        return lifecycle;
    }
}
