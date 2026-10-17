package com.lingsql.datasource;

import com.lingsql.common.exception.BizException;
import com.lingsql.common.exception.ErrorCode;
import com.lingsql.datasource.model.TenantDbConfig;
import com.lingsql.datasource.repository.TenantDbConfigRepository;
import com.lingsql.rbac.authorization.AuthorizationChange;
import com.lingsql.rbac.authorization.AuthorizationChangeKind;
import com.lingsql.rbac.authorization.IAuthorizationSessionLifecycle;
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
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

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

    private static final int CONNECTION_TIMEOUT_SECONDS = 8;
    private static final int MAX_ALLOWLIST_TABLES = 100;
    private static final Set<String> READ_ONLY_PRIVILEGES = Set.of("USAGE", "SELECT", "SHOW VIEW");
    private static final Pattern MYSQL_IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_$]{0,63}");

    private final TenantDbConfigRepository repository;
    private final DataSourceManager dataSourceManager;
    private final MetadataCacheService metadataCacheService;
    private final IAuthorizationSessionLifecycle authorizationSessionLifecycle;
    private final TenantJdbcUrlValidator jdbcUrlValidator;

    public List<TenantDbConfig> list() {
        return repository.findAll();
    }

    @Transactional
    public TenantDbConfig create(TenantDbConfig request) {
        verifyReadOnlyConnection(request);
        request.setId(null);
        request.setTenantId(normalizeTenantId(request.getTenantId()));
        request.setSchemaSyncStatus(0);
        request.setLastSyncTime(null);
        resetSchemaReceipt(request);
        return authorizationSessionLifecycle.publish(
                AuthorizationChange.tenant(request.getTenantId(), AuthorizationChangeKind.DATA_BOUNDARY),
                () -> repository.save(request)
        );
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
        resetSchemaReceipt(config);
        verifyReadOnlyConnection(config);
        TenantDbConfig saved;
        if (Objects.equals(previousTenantId, config.getTenantId())) {
            saved = authorizationSessionLifecycle.publish(
                    AuthorizationChange.tenant(config.getTenantId(), AuthorizationChangeKind.DATA_BOUNDARY),
                    () -> repository.save(config)
            );
        } else {
            saved = authorizationSessionLifecycle.publish(
                    AuthorizationChange.tenant(previousTenantId, AuthorizationChangeKind.DATA_BOUNDARY),
                    () -> authorizationSessionLifecycle.publish(
                            AuthorizationChange.tenant(config.getTenantId(), AuthorizationChangeKind.DATA_BOUNDARY),
                            () -> repository.save(config)
                    )
            );
        }
        invalidateAfterCommit(previousTenantId, saved.getTenantId());
        return saved;
    }

    @Transactional
    public void delete(Long id) {
        TenantDbConfig config = repository.findById(id)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "租户配置不存在"));
        authorizationSessionLifecycle.publish(
                AuthorizationChange.tenant(config.getTenantId(), AuthorizationChangeKind.DATA_BOUNDARY),
                () -> {
                    repository.delete(config);
                    return null;
                }
        );
        invalidateAfterCommit(config.getTenantId(), null);
    }

    public void testConnection(TenantDbConfig request) {
        verifyConnection(request, false);
    }

    /**
     * 验证开户数据源能够连接，且当前 MySQL 账号没有写入或授权权限。
     *
     * @param request 数据源配置
     * @author 余炘洋
     * @date 2026-08-15
     */
    public void verifyReadOnlyConnection(TenantDbConfig request) {
        verifyConnection(request, true);
    }

    private void resetSchemaReceipt(TenantDbConfig config) {
        config.setSchemaTableCount(null);
        config.setSchemaWhitelistTableCount(null);
        config.setSchemaWhitelistDigest(null);
        config.setSchemaSyncToken(null);
    }

    private void verifyConnection(TenantDbConfig request, boolean requireReadOnly) {
        if (request == null || !StringUtils.hasText(request.getJdbcUrl())) {
            throw new BizException(ErrorCode.CONFIG_INVALID, "数据库连接地址不能为空");
        }
        jdbcUrlValidator.validate(request.getJdbcUrl());
        if (requireReadOnly && !StringUtils.hasText(request.getWhiteListColumns())) {
            throw new BizException(ErrorCode.CONFIG_INVALID, "字段白名单不能为空");
        }
        String driverClass = jdbcUrlValidator.resolveDriverClass(request.getDriverClass());
        String password = resolvePassword(request);
        try {
            Class.forName(driverClass);
            DriverManager.setLoginTimeout(CONNECTION_TIMEOUT_SECONDS);
            try (Connection connection = DriverManager.getConnection(
                    request.getJdbcUrl(), request.getUsername(), password);
                 Statement statement = connection.createStatement()) {
                statement.setQueryTimeout(CONNECTION_TIMEOUT_SECONDS);
                statement.execute("SELECT 1");
                if (requireReadOnly) {
                    verifyReadOnlyGrants(statement);
                    verifyAllowlistReadable(statement, request.getWhiteListTables());
                }
            }
        } catch (BizException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("测试租户数据库连接失败: driverClass={}", driverClass, ex);
            throw new BizException(ErrorCode.CONFIG_INVALID, "连接失败，请检查数据库配置后重试", ex);
        }
    }

    private void verifyReadOnlyGrants(Statement statement) throws SQLException {
        boolean selectGranted = false;
        try (ResultSet resultSet = statement.executeQuery("SHOW GRANTS FOR CURRENT_USER")) {
            while (resultSet.next()) {
                String grant = resultSet.getString(1);
                String privileges = parsePrivilegeSegment(grant);
                for (String privilege : privileges.split(",")) {
                    String normalized = privilege.trim();
                    if (!READ_ONLY_PRIVILEGES.contains(normalized)) {
                        throw new BizException(ErrorCode.CONFIG_INVALID, "数据库账号包含写入或管理权限，请改用只读账号");
                    }
                    if ("SELECT".equals(normalized)) {
                        selectGranted = true;
                    }
                }
            }
        }
        if (!selectGranted) {
            throw new BizException(ErrorCode.CONFIG_INVALID, "数据库账号缺少 SELECT 权限");
        }
    }

    private String parsePrivilegeSegment(String grant) {
        if (!StringUtils.hasText(grant)) {
            throw new BizException(ErrorCode.CONFIG_INVALID, "无法识别数据库账号授权信息");
        }
        String normalized = grant.trim().toUpperCase(Locale.ROOT);
        if (!normalized.startsWith("GRANT ") || normalized.contains(" WITH GRANT OPTION")) {
            throw new BizException(ErrorCode.CONFIG_INVALID, "数据库账号包含写入或管理权限，请改用只读账号");
        }
        int onIndex = normalized.indexOf(" ON ");
        if (onIndex <= "GRANT ".length()) {
            throw new BizException(ErrorCode.CONFIG_INVALID, "数据库账号通过角色获得权限，暂不能证明为只读账号");
        }
        return normalized.substring("GRANT ".length(), onIndex).trim();
    }

    private void verifyAllowlistReadable(Statement statement, String whiteListTables) throws SQLException {
        if (!StringUtils.hasText(whiteListTables)) {
            throw new BizException(ErrorCode.CONFIG_INVALID, "表白名单不能为空");
        }
        List<String> tables = java.util.Arrays.stream(whiteListTables.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        if (tables.isEmpty() || tables.size() > MAX_ALLOWLIST_TABLES) {
            throw new BizException(ErrorCode.CONFIG_INVALID, "表白名单数量必须在 1 到 100 之间");
        }
        for (String table : tables) {
            if (!MYSQL_IDENTIFIER.matcher(table).matches()) {
                throw new BizException(ErrorCode.CONFIG_INVALID, "表白名单包含非法标识符");
            }
            // 表标识符已由 MYSQL_IDENTIFIER 白名单字符集严格校验（仅字母、数字、下划线、$，不含反引号），
            // 因此此处拼接的是受控标识符，不存在 SQL 注入可能；标识符无法使用占位符参数化。
            statement.executeQuery("SELECT 1 FROM `" + table + "` LIMIT 0").close();
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
