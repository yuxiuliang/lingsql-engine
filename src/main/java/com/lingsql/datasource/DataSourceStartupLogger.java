package com.lingsql.datasource;

import com.lingsql.config.LingSqlProperties;
import com.zaxxer.hikari.HikariDataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

/**
 * 启动时输出连接池关键信息，便于排查“未知连接池”日志。
 *
 * @author 余炘洋
 * @date 2026-01-22
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataSourceStartupLogger implements ApplicationRunner {

    private final DataSource adminDataSource;
    private final LingSqlProperties properties;

    /**
     * Spring 启动完成后打印主库连接池信息与租户默认参数。
     *
     * @param args 启动参数
     * @author 余炘洋
     * @date 2026-01-22
     */
    @Override
    public void run(ApplicationArguments args) {
        logAdminPoolInfo();
        logTenantDefaults();
    }

    /**
     * 输出主库连接池参数（不含密码）。
     *
     * @author 余炘洋
     * @date 2026-01-22
     */
    private void logAdminPoolInfo() {
        if (adminDataSource instanceof HikariDataSource hikari) {
            log.info("主库连接池信息: poolName={}, jdbcUrl={}, driverClassName={}, minIdle={}, maxPoolSize={}, connectionTimeoutMs={}, idleTimeoutMs={}",
                    hikari.getPoolName(),
                    hikari.getJdbcUrl(),
                    hikari.getDriverClassName(),
                    hikari.getMinimumIdle(),
                    hikari.getMaximumPoolSize(),
                    hikari.getConnectionTimeout(),
                    hikari.getIdleTimeout());
            return;
        }
        log.warn("主库数据源不是 HikariDataSource，当前类型={}", adminDataSource.getClass().getName());
    }

    /**
     * 输出租户默认连接池参数。
     *
     * @author 余炘洋
     * @date 2026-01-22
     */
    private void logTenantDefaults() {
        LingSqlProperties.Hikari hikari = properties.getHikari();
        log.info("租户默认连接池参数: maxPoolSize={}, minIdle={}, idleTimeoutMs={}, connectionTimeoutMs={}",
                hikari.getMaximumPoolSize(),
                hikari.getMinimumIdle(),
                hikari.getIdleTimeout().toMillis(),
                hikari.getConnectionTimeout().toMillis());
    }
}
