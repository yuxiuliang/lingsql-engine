package com.lingsql.tenant.service;

import com.lingsql.datasource.repository.TenantDbConfigRepository;
import com.lingsql.tenant.entity.TenantInfo;
import com.lingsql.tenant.model.TenantLifecycleSnapshot;
import com.lingsql.tenant.model.TenantLifecycleState;
import com.lingsql.tenant.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 租户生命周期巡检与自动冻结任务。
 *
 * @author 余炘洋
 * @date 2026-02-27
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TenantLifecycleScheduler {

    private static final Integer NOT_DELETED = 0;
    private static final Integer ACTIVE_STATUS = 1;
    private static final Integer INACTIVE_STATUS = 0;

    private final TenantRepository tenantRepository;
    private final TenantDbConfigRepository tenantDbConfigRepository;
    private final TenantLifecycleService lifecycleService;

    /**
     * 定时冻结超过宽限期的租户。
     *
     * @author 余炘洋
     * @date 2026-02-27
     */
    @Scheduled(cron = "${lingsql.tenant-lifecycle.auto-suspend-cron:0 0/30 * * * ?}")
    public void autoSuspendExpiredTenants() {
        if (!lifecycleService.isLifecycleEnabled() || !lifecycleService.isAutoSuspendEnabled()) {
            return;
        }
        List<TenantInfo> activeTenants = tenantRepository
                .findByIsDeletedAndStatusAndExpireTimeIsNotNull(NOT_DELETED, ACTIVE_STATUS);
        if (activeTenants == null || activeTenants.isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        int suspendCount = 0;
        for (TenantInfo tenant : activeTenants) {
            TenantLifecycleSnapshot snapshot = lifecycleService.evaluate(tenant, now);
            if (snapshot.state() != TenantLifecycleState.SUSPENDED) {
                continue;
            }
            tenant.setStatus(INACTIVE_STATUS);
            tenant.setUpdateTime(now);
            tenantRepository.save(tenant);
            tenantDbConfigRepository.findByTenantId(tenant.getTenantId()).ifPresent(config -> {
                config.setStatus(INACTIVE_STATUS);
                tenantDbConfigRepository.save(config);
            });
            suspendCount++;
            log.warn("租户已自动冻结: tenantId={}, expireTime={}", tenant.getTenantId(), tenant.getExpireTime());
        }
        if (suspendCount > 0) {
            log.warn("租户生命周期自动冻结完成: suspendedCount={}", suspendCount);
        }
    }
}
