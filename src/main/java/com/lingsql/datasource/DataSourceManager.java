package com.lingsql.datasource;

import com.lingsql.config.LingSqlProperties;
import com.lingsql.datasource.model.TenantDbConfig;
import com.lingsql.datasource.repository.TenantDbConfigRepository;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
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

    /**
     * 缓存租户 ID 与对应的 HikariDataSource。
     *
     * @author 余炘洋
     * @date 2026-01-22
     */
    private final Map<String, DataSource> dataSourceMap = new ConcurrentHashMap<>();

    /**
     * 获取指定租户的数据源，如果不存在则动态创建。
     *
     * @param tenantId 租户标识
     * @return 数据源
     * @author 余炘洋
     * @date 2026-01-22
     */
    public DataSource getDataSource(String tenantId) {
        return dataSourceMap.computeIfAbsent(tenantId, this::createTenantDataSource);
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
                .orElseThrow(() -> new RuntimeException("未找到租户配置: " + tenantId));

        if (config.getStatus() != null && config.getStatus() == 0) {
            throw new RuntimeException("租户配置已禁用: " + tenantId);
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
        DataSource ds = dataSourceMap.remove(tenantId);
        if (ds instanceof HikariDataSource hds) {
            hds.close();
        }
    }
}
