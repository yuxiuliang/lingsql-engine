package com.lingsql.ops;

import com.lingsql.datasource.DataSourceManager;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * 租户连接池健康指示器。
 *
 * @author 余炘洋
 * @date 2026-02-27
 */
@Component("tenantPool")
@RequiredArgsConstructor
public class TenantPoolHealthIndicator implements HealthIndicator {

    private final DataSourceManager dataSourceManager;

    @Override
    public Health health() {
        int activePools = dataSourceManager.activeTenantPoolCount();
        int maxPools = dataSourceManager.maxTenantPoolSize();
        double utilization = dataSourceManager.tenantPoolUtilization();
        Health.Builder builder = Health.up();
        if (maxPools > 0 && activePools > maxPools) {
            builder = Health.down();
        }
        return builder
                .withDetail("activePools", activePools)
                .withDetail("maxPools", maxPools)
                .withDetail("utilization", utilization)
                .build();
    }
}

