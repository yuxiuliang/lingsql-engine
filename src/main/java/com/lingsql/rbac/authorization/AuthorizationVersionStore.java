package com.lingsql.rbac.authorization;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 授权版本的 MySQL 权威存储。
 *
 * @author 余炘洋
 * @date 2026-08-16
 */
@Repository
@RequiredArgsConstructor
class AuthorizationVersionStore {

    private final JdbcTemplate jdbcTemplate;

    void bumpRequired(AuthorizationTargetType targetType, String targetId) {
        int updated = switch (targetType) {
            case USER -> jdbcTemplate.update(
                    "UPDATE t_lingsql_engine_user "
                            + "SET authorization_version = authorization_version + 1 WHERE id = ?",
                    parseNumericId(targetType, targetId)
            );
            case ROLE -> jdbcTemplate.update(
                    "UPDATE t_lingsql_engine_role "
                            + "SET authorization_version = authorization_version + 1 WHERE id = ?",
                    parseNumericId(targetType, targetId)
            );
            case TENANT -> jdbcTemplate.update(
                    "UPDATE t_lingsql_engine_tenant "
                            + "SET authorization_version = authorization_version + 1 "
                            + "WHERE tenant_id = ?",
                    targetId
            );
        };
        if (updated != 1) {
            throw new AuthorizationStateUnavailableException(
                    "授权版本缺失，拒绝提交变更: scopeType=" + targetType + ", scopeId=" + targetId
            );
        }
    }

    Map<VersionKey, Long> requireVersions(List<VersionKey> keys) {
        if (keys == null || keys.isEmpty()) {
            throw new AuthorizationStateUnavailableException("授权版本读取目标不能为空");
        }
        Map<VersionKey, Long> versions = new HashMap<>();
        loadUserVersions(keys, versions);
        loadTenantVersions(keys, versions);
        loadRoleVersions(keys, versions);
        for (VersionKey key : keys) {
            Long version = versions.get(key);
            if (version == null || version < 1L) {
                throw new AuthorizationStateUnavailableException(
                        "授权版本缺失: scopeType=" + key.targetType() + ", scopeId=" + key.targetId()
                );
            }
        }
        return versions;
    }

    private void loadUserVersions(List<VersionKey> keys, Map<VersionKey, Long> versions) {
        for (VersionKey key : keys) {
            if (key.targetType() != AuthorizationTargetType.USER) {
                continue;
            }
            List<Long> rows = jdbcTemplate.queryForList(
                    "SELECT authorization_version FROM t_lingsql_engine_user WHERE id = ? AND status = 1",
                    Long.class,
                    parseNumericId(key.targetType(), key.targetId())
            );
            putSingleVersion(key, rows, versions);
        }
    }

    private void loadTenantVersions(List<VersionKey> keys, Map<VersionKey, Long> versions) {
        for (VersionKey key : keys) {
            if (key.targetType() != AuthorizationTargetType.TENANT) {
                continue;
            }
            List<Long> rows = jdbcTemplate.queryForList(
                    "SELECT authorization_version FROM t_lingsql_engine_tenant "
                            + "WHERE tenant_id = ? AND is_deleted = 0 AND status = 1",
                    Long.class,
                    key.targetId()
            );
            putSingleVersion(key, rows, versions);
        }
    }

    private void loadRoleVersions(List<VersionKey> keys, Map<VersionKey, Long> versions) {
        List<VersionKey> roleKeys = keys.stream()
                .filter(key -> key.targetType() == AuthorizationTargetType.ROLE)
                .toList();
        if (roleKeys.isEmpty()) {
            return;
        }
        String placeholders = roleKeys.stream().map(key -> "?").collect(Collectors.joining(","));
        Object[] roleIds = roleKeys.stream()
                .map(key -> parseNumericId(key.targetType(), key.targetId()))
                .toArray();
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, authorization_version FROM t_lingsql_engine_role WHERE id IN (" + placeholders + ")",
                roleIds
        );
        for (Map<String, Object> row : rows) {
            Object rawId = row.get("id");
            Object rawVersion = row.get("authorization_version");
            if (rawId instanceof Number id && rawVersion instanceof Number version) {
                versions.put(
                        new VersionKey(AuthorizationTargetType.ROLE, String.valueOf(id.longValue())),
                        version.longValue()
                );
            }
        }
    }

    private void putSingleVersion(VersionKey key,
                                  List<Long> rows,
                                  Map<VersionKey, Long> versions) {
        if (rows != null && rows.size() == 1 && rows.get(0) != null) {
            versions.put(key, rows.get(0));
        }
    }

    private long parseNumericId(AuthorizationTargetType targetType, String targetId) {
        try {
            return Long.parseLong(targetId);
        } catch (NumberFormatException ex) {
            throw new AuthorizationStateUnavailableException(
                    "授权版本主体格式错误: scopeType=" + targetType + ", scopeId=" + targetId,
                    ex
            );
        }
    }

    record VersionKey(AuthorizationTargetType targetType, String targetId) {
    }
}
