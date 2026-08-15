package com.lingsql.datasource;

import com.lingsql.common.exception.BizException;
import com.lingsql.common.exception.ErrorCode;
import com.lingsql.datasource.model.TenantDbConfig;
import com.lingsql.datasource.repository.TenantDbConfigRepository;
import com.lingsql.schema.MetadataCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;

/**
 * 租户数据源配置服务，统一管理配置持久化与运行时缓存失效。
 *
 * @author 余炘洋
 * @date 2026-08-15
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantDataSourceConfigService {

    private static final String DEFAULT_DRIVER_CLASS = "com.mysql.cj.jdbc.Driver";
    private static final int CONNECTION_TIMEOUT_SECONDS = 8;

    private final TenantDbConfigRepository repository;
    private final DataSourceManager dataSourceManager;
    private final MetadataCacheService metadataCacheService;

    public List<TenantDbConfig> list() {
        return repository.findAll();
    }

    @Transactional
    public TenantDbConfig create(TenantDbConfig request) {
        request.setId(null);
        request.setTenantId(normalizeTenantId(request.getTenantId()));
        request.setSchemaSyncStatus(0);
        request.setLastSyncTime(null);
        return repository.save(request);
    }

    @Transactional
    public TenantDbConfig update(Long id, TenantDbConfig request) {
        TenantDbConfig config = repository.findById(id)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "租户配置不存在"));
        String previousTenantId = config.getTenantId();
        config.setTenantId(normalizeTenantId(request.getTenantId()));
        config.setJdbcUrl(request.getJdbcUrl());
        config.setUsername(request.getUsername());
        if (StringUtils.hasText(request.getPassword())) {
            config.setPassword(request.getPassword());
        }
        config.setDriverClass(request.getDriverClass());
        config.setIsolationMode(request.getIsolationMode());
        config.setLogicColumn(request.getLogicColumn());
        config.setWhiteListTables(request.getWhiteListTables());
        config.setWhiteListColumns(request.getWhiteListColumns());
        config.setSensitiveColumns(request.getSensitiveColumns());
        config.setStatus(request.getStatus());
        config.setSchemaSyncStatus(0);
        config.setLastSyncTime(null);
        TenantDbConfig saved = repository.save(config);
        invalidateAfterCommit(previousTenantId, saved.getTenantId());
        return saved;
    }

    @Transactional
    public void delete(Long id) {
        TenantDbConfig config = repository.findById(id)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "租户配置不存在"));
        repository.delete(config);
        invalidateAfterCommit(config.getTenantId(), null);
    }

    public void testConnection(TenantDbConfig request) {
        String driverClass = StringUtils.hasText(request.getDriverClass())
                ? request.getDriverClass().trim()
                : DEFAULT_DRIVER_CLASS;
        String password = resolvePassword(request);
        try {
            Class.forName(driverClass);
            DriverManager.setLoginTimeout(CONNECTION_TIMEOUT_SECONDS);
            try (Connection connection = DriverManager.getConnection(
                    request.getJdbcUrl(), request.getUsername(), password);
                 Statement statement = connection.createStatement()) {
                statement.setQueryTimeout(CONNECTION_TIMEOUT_SECONDS);
                statement.execute("SELECT 1");
            }
        } catch (Exception ex) {
            log.warn("测试租户数据库连接失败: driverClass={}, jdbcUrl={}",
                    driverClass, request.getJdbcUrl(), ex);
            throw new BizException(ErrorCode.CONFIG_INVALID, "连接失败，请检查数据库配置后重试", ex);
        }
    }

    private String resolvePassword(TenantDbConfig request) {
        if (StringUtils.hasText(request.getPassword()) || request.getId() == null) {
            return request.getPassword();
        }
        return repository.findById(request.getId()).map(TenantDbConfig::getPassword).orElse(null);
    }

    private void invalidateAfterCommit(String firstTenantId, String secondTenantId) {
        Runnable invalidation = () -> {
            invalidate(firstTenantId);
            if (!StringUtils.hasText(firstTenantId) || !firstTenantId.equals(secondTenantId)) {
                invalidate(secondTenantId);
            }
        };
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            invalidation.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                invalidation.run();
            }
        });
    }

    private void invalidate(String tenantId) {
        if (!StringUtils.hasText(tenantId)) {
            return;
        }
        dataSourceManager.removeDataSource(tenantId);
        metadataCacheService.evictSchema(tenantId);
    }

    private String normalizeTenantId(String tenantId) {
        if (!StringUtils.hasText(tenantId)) {
            throw new BizException(ErrorCode.INVALID_PARAM, "租户ID不能为空");
        }
        return tenantId.trim();
    }
}
