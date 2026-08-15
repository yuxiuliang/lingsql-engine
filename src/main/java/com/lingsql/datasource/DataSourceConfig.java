package com.lingsql.datasource;


import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import javax.sql.DataSource;

/**
 * 数据源配置，包含主库与动态租户数据源。
 *
 * @author 余炘洋
 * @date 2026-01-22
 */
@Configuration
public class DataSourceConfig {

    /**
     * 主配置库的数据源属性（对应 yml 中的 spring.datasource）。
     *
     * @return 主库数据源属性
     * @author 余炘洋
     * @date 2026-01-22
     */
    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource")
    public DataSourceProperties adminDataSourceProperties() {
        return new DataSourceProperties();
    }

    /**
     * 主库 Hikari 配置。
     *
     * @return HikariConfig
     * @author 余炘洋
     * @date 2026-01-22
     */
    @Bean
    @ConfigurationProperties("spring.datasource.hikari")
    public HikariConfig adminHikariConfig() {
        return new HikariConfig();
    }

    /**
     * 主配置库数据源：用于 JPA 存放租户信息和模型信息。
     *
     * @return 主库数据源
     * @author 余炘洋
     * @date 2026-01-22
     */
    @Bean
    @Primary
    public DataSource adminDataSource(DataSourceProperties properties, HikariConfig adminHikariConfig) {
        if (adminHikariConfig.getJdbcUrl() == null) {
            adminHikariConfig.setJdbcUrl(properties.determineUrl());
        }
        if (adminHikariConfig.getUsername() == null) {
            adminHikariConfig.setUsername(properties.determineUsername());
        }
        if (adminHikariConfig.getPassword() == null) {
            adminHikariConfig.setPassword(properties.determinePassword());
        }
        if (adminHikariConfig.getDriverClassName() == null && properties.determineDriverClassName() != null) {
            adminHikariConfig.setDriverClassName(properties.determineDriverClassName());
        }
        return new HikariDataSource(adminHikariConfig);
    }

    /**
     * 控制库专用命名参数模板，防止账本等控制面写入被路由到租户业务库。
     *
     * @param adminDataSource 主配置库数据源
     * @return 控制库命名参数模板
     * @author 余炘洋
     * @date 2026-08-15
     */
    @Bean
    @Primary
    public NamedParameterJdbcTemplate adminNamedParameterJdbcTemplate(
            @Qualifier("adminDataSource") DataSource adminDataSource) {
        return new NamedParameterJdbcTemplate(adminDataSource);
    }

    /**
     * 动态数据源：用于执行 Text-to-SQL 的业务查询。
     *
     * @param dataSourceManager 动态数据源管理器
     * @param adminDataSource   主库数据源
     * @return 动态数据源
     * @author 余炘洋
     * @date 2026-01-22
     */
    @Bean
    public DynamicDataSource dynamicDataSource(DataSourceManager dataSourceManager, DataSource adminDataSource) {
        return new DynamicDataSource(dataSourceManager, adminDataSource);
    }

    /**
     * 业务执行专用的 JdbcTemplate，绑定动态数据源。
     *
     * @param dynamicDataSource 动态数据源
     * @return JdbcTemplate
     * @author 余炘洋
     * @date 2026-01-22
     */
    @Bean
    public JdbcTemplate businessJdbcTemplate(DynamicDataSource dynamicDataSource) {
        return new JdbcTemplate(dynamicDataSource);
    }
}
