package com.lingsql.datasource;

import com.lingsql.common.exception.BizException;
import com.lingsql.common.exception.ErrorCode;
import com.lingsql.config.LingSqlProperties;
import com.lingsql.datasource.model.TenantDbConfig;
import com.lingsql.datasource.repository.TenantDbConfigRepository;
import com.lingsql.executor.SqlTaskTracker;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 多租户数据源管理器，负责懒加载 Hikari 连接池。
 *
 * @author 余炘洋
 * @date 2026-01-22
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataSourceManager {

    private final TenantDbConfigRepository repository;
    private final LingSqlProperties properties;
    private final SqlTaskTracker sqlTaskTracker;

    /**
     * 缓存租户 ID 与对应的 HikariDataSource。
     *
     * @author 余炘洋
     * @date 2026-01-22
     */
    private final Map<String, CachedDataSource> dataSourceMap = new ConcurrentHashMap<>();

    /**
     * 获取指定租户的数据源，如果不存在则动态创建。
     *
     * @param tenantId 租户标识
     * @return 数据源
     * @author 余炘洋
     * @date 2026-01-22
     */
    public DataSource getDataSource(String tenantId) {
        if (!StringUtils.hasText(tenantId)) {
            throw new BizException(ErrorCode.INVALID_PARAM, "租户ID不能为空");
        }
        long now = System.currentTimeMillis();
        CachedDataSource cached = dataSourceMap.compute(tenantId, (key, existing) -> {
            if (existing != null && !isExpired(existing, now)) {
                existing.touch(now);
                return existing;
            }
            if (existing != null) {
                closeDataSource(existing);
            }
            DataSource created = createTenantDataSource(key);
            return new CachedDataSource(key, created, now);
        });
        enforceCapacity(now);
        return cached.getDataSource();
    }

    /**
     * 创建租户数据源。
     *
     * @param tenantId 租户标识
     * @return 数据源
     * @author 余炘洋
     * @date 2026-01-22
     */
    private DataSource createTenantDataSource(String tenantId) {
        log.info("开始为租户 [{}] 动态初始化连接池...", tenantId);

        // 从配置库中读取连接信息
        TenantDbConfig config = repository.findByTenantId(tenantId)
                .orElseThrow(() -> new BizException(ErrorCode.TENANT_NOT_FOUND, "未找到租户配置"));

        if (config.getStatus() != null && config.getStatus() == 0) {
            throw new BizException(ErrorCode.FORBIDDEN, "租户配置已禁用");
        }

        // 配置 HikariCP
        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl(config.getJdbcUrl());
        hikariConfig.setUsername(config.getUsername());
        hikariConfig.setPassword(config.getPassword());
        hikariConfig.setDriverClassName(config.getDriverClass());

        // 性能优化参数
        hikariConfig.setPoolName("HikariPool-" + tenantId);
        hikariConfig.setMaximumPoolSize(properties.getHikari().getMaximumPoolSize());
        hikariConfig.setMinimumIdle(properties.getHikari().getMinimumIdle());
        hikariConfig.setIdleTimeout(properties.getHikari().getIdleTimeout().toMillis());
        hikariConfig.setConnectionTimeout(properties.getHikari().getConnectionTimeout().toMillis());

        log.info("租户 [{}] 连接池初始化成功", tenantId);
        return new HikariDataSource(hikariConfig);
    }

    /**
     * 当配置变更时，用于刷新/移除旧的连接池。
     *
     * @param tenantId 租户标识
     * @author 余炘洋
     * @date 2026-01-22
     */
    public void removeDataSource(String tenantId) {
        CachedDataSource cached = dataSourceMap.remove(tenantId);
        if (cached != null) {
            closeDataSource(cached);
        }
    }

    /**
     * 当前活跃租户连接池数量。
     *
     * @return 活跃连接池数量
     * @author 余炘洋
     * @date 2026-02-27
     */
    public int activeTenantPoolCount() {
        return dataSourceMap.size();
    }

    /**
     * 租户连接池配置上限。
     *
     * @return 最大连接池数量
     * @author 余炘洋
     * @date 2026-02-27
     */
    public int maxTenantPoolSize() {
        return resolveMaxTenantPools();
    }

    /**
     * 连接池使用率（活跃池数/上限）。
     *
     * @return 使用率，范围 [0, 1]
     * @author 余炘洋
     * @date 2026-02-27
     */
    public double tenantPoolUtilization() {
        int max = resolveMaxTenantPools();
        if (max <= 0) {
            return 0D;
        }
        double ratio = (double) dataSourceMap.size() / (double) max;
        if (ratio < 0D) {
            return 0D;
        }
        return Math.min(ratio, 1D);
    }

    @org.springframework.scheduling.annotation.Scheduled(
            fixedDelayString = "${lingsql.hikari.tenant-cache-clean-interval-ms:300000}")
    public void evictExpiredSources() {
        long now = System.currentTimeMillis();
        long ttlMs = resolveTenantCacheTtlMs();
        if (ttlMs <= 0) {
            return;
        }
        for (Map.Entry<String, CachedDataSource> entry : dataSourceMap.entrySet()) {
            CachedDataSource cached = entry.getValue();
            if (sqlTaskTracker.hasRunning(entry.getKey())) {
                continue;
            }
            if (cached != null && isExpired(cached, now)) {
                if (dataSourceMap.remove(entry.getKey(), cached)) {
                    closeDataSource(cached);
                }
            }
        }
    }

    private void enforceCapacity(long now) {
        int maxPools = resolveMaxTenantPools();
        if (maxPools <= 0) {
            return;
        }
        if (dataSourceMap.size() <= maxPools) {
            return;
        }
        var entries = new ArrayList<>(dataSourceMap.entrySet());
        entries.sort(Comparator.comparingLong(entry -> entry.getValue().getLastAccessMs()));
        for (Map.Entry<String, CachedDataSource> entry : entries) {
            if (dataSourceMap.size() <= maxPools) {
                return;
            }
            if (sqlTaskTracker.hasRunning(entry.getKey())) {
                continue;
            }
            if (dataSourceMap.remove(entry.getKey(), entry.getValue())) {
                closeDataSource(entry.getValue());
            }
        }
    }

    private boolean isExpired(CachedDataSource cached, long now) {
        long ttlMs = resolveTenantCacheTtlMs();
        if (ttlMs <= 0) {
            return false;
        }
        return now - cached.getLastAccessMs() >= ttlMs;
    }

    private long resolveTenantCacheTtlMs() {
        LingSqlProperties.Hikari hikari = properties.getHikari();
        if (hikari == null) {
            return 0;
        }
        Duration ttl = hikari.getTenantCacheTtl();
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            return 0;
        }
        return ttl.toMillis();
    }

    private int resolveMaxTenantPools() {
        LingSqlProperties.Hikari hikari = properties.getHikari();
        if (hikari == null) {
            return 0;
        }
        return hikari.getMaxTenantPools();
    }

    private void closeDataSource(CachedDataSource cached) {
        DataSource ds = cached == null ? null : cached.getDataSource();
        if (ds instanceof HikariDataSource hds) {
            hds.close();
        }
    }

    /**
     * 缓存租户数据源与访问时间。
     *
     * @author 余炘洋
     * @date 2026-01-28
     */
    private static class CachedDataSource {
        private final String tenantId;
        private final DataSource dataSource;
        private volatile long lastAccessMs;

        private CachedDataSource(String tenantId, DataSource dataSource, long now) {
            this.tenantId = tenantId;
            this.dataSource = dataSource;
            this.lastAccessMs = now;
        }

        public DataSource getDataSource() {
            return dataSource;
        }

        public long getLastAccessMs() {
            return lastAccessMs;
        }

        public void touch(long now) {
            this.lastAccessMs = now;
        }
    }
}
