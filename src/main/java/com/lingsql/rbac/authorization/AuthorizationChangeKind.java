package com.lingsql.rbac.authorization;

/**
 * 授权事实变更类型。
 *
 * @author 余炘洋
 * @date 2026-08-16
 */
public enum AuthorizationChangeKind {
    USER_ACCESS,
    ROLE_ACCESS,
    TENANT_PERMISSION_POOL,
    ORGANIZATION_SCOPE,
    EXPERIENCE_POLICY,
    TENANT_LIFECYCLE,
    DATA_BOUNDARY
}
