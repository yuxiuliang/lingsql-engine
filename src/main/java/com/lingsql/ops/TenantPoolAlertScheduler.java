package com.lingsql.ops;

import com.lingsql.config.LingSqlProperties;
import com.lingsql.datasource.DataSourceManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 租户连接池告警任务。
 *
 * @author 余炘洋
 * @date 2026-02-27
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TenantPoolAlertScheduler {

    private final DataSourceManager dataSourceManager;
    private final OpsAlertService opsAlertService;
    private final LingSqlProperties properties;

    private final AtomicLong lastPoolAlertAt = new AtomicLong(0L);

    /**
     * 定时检查租户连接池压力并触发告警。
     *
     * @author 余炘洋
     * @date 2026-02-27
     */
    @Scheduled(fixedDelayString = "${lingsql.alert.pool-check-interval-ms:60000}")
    public void checkTenantPoolPressure() {
        LingSqlProperties.Alert alert = properties.getAlert();
        if (alert == null || !alert.isEnabled()) {
            return;
        }
        int activePools = dataSourceManager.activeTenantPoolCount();
        int maxPools = dataSourceManager.maxTenantPoolSize();
        if (maxPools <= 0) {
            return;
        }
        double utilization = dataSourceManager.tenantPoolUtilization();
        double threshold = normalizeThreshold(alert.getTenantPoolUtilizationThreshold());
        if (utilization < threshold && activePools <= maxPools) {
            return;
        }
        if (inCooldown(alert.getCooldown())) {
            return;
        }
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("activePools", activePools);
        details.put("maxPools", maxPools);
        details.put("utilization", utilization);
        details.put("threshold", threshold);
        boolean success = opsAlertService.sendAlert(
                "租户连接池压力告警",
                "租户连接池使用率达到告警阈值，请检查租户流量与数据库连接池配置",
                details
        );
        if (success) {
            lastPoolAlertAt.set(System.currentTimeMillis());
        } else {
            log.warn("租户连接池告警发送失败: active={}, max={}, utilization={}", activePools, maxPools, utilization);
        }
    }

    private boolean inCooldown(Duration cooldown) {
        long now = System.currentTimeMillis();
        long lastAt = lastPoolAlertAt.get();
        long cooldownMillis = resolveCooldownMillis(cooldown);
        return cooldownMillis > 0 && now - lastAt < cooldownMillis;
    }

    private long resolveCooldownMillis(Duration cooldown) {
        if (cooldown == null || cooldown.isZero() || cooldown.isNegative()) {
            return Duration.ofMinutes(5).toMillis();
        }
        return cooldown.toMillis();
    }

    private double normalizeThreshold(double threshold) {
        if (threshold <= 0D) {
            return 0.9D;
        }
        return Math.min(threshold, 1D);
    }
}

