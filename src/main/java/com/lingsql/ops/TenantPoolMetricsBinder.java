package com.lingsql.ops;

import com.lingsql.datasource.DataSourceManager;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 租户连接池指标绑定器。
 *
 * @author 余炘洋
 * @date 2026-02-27
 */
@Component
@RequiredArgsConstructor
public class TenantPoolMetricsBinder implements MeterBinder {

    private final DataSourceManager dataSourceManager;

    @Override
    public void bindTo(MeterRegistry registry) {
        Gauge.builder("lingsql.tenant.pool.active", dataSourceManager, manager -> (double) manager.activeTenantPoolCount())
                .description("活跃租户连接池数量")
                .baseUnit("pools")
                .register(registry);
        Gauge.builder("lingsql.tenant.pool.max", dataSourceManager, manager -> (double) manager.maxTenantPoolSize())
                .description("租户连接池配置上限")
                .baseUnit("pools")
                .register(registry);
        Gauge.builder("lingsql.tenant.pool.utilization", dataSourceManager, DataSourceManager::tenantPoolUtilization)
                .description("租户连接池使用率")
                .register(registry);
    }
}

