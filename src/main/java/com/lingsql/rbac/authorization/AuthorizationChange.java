package com.lingsql.rbac.authorization;

import org.springframework.util.StringUtils;

/**
 * 授权事实变更目标。
 *
 * @param targetType 目标类型
 * @param targetId 目标标识
 * @param kind 变更类型
 * @author 余炘洋
 * @date 2026-08-16
 */
public record AuthorizationChange(
        AuthorizationTargetType targetType,
        String targetId,
        AuthorizationChangeKind kind
) {

    public AuthorizationChange {
        if (targetType == null || kind == null || !StringUtils.hasText(targetId)) {
            throw new IllegalArgumentException("授权变更目标和类型不能为空");
        }
        targetId = targetId.trim();
    }

    public static AuthorizationChange user(Long userId, AuthorizationChangeKind kind) {
        if (userId == null) {
            throw new IllegalArgumentException("用户ID不能为空");
        }
        return new AuthorizationChange(AuthorizationTargetType.USER, String.valueOf(userId), kind);
    }

    public static AuthorizationChange role(Long roleId, AuthorizationChangeKind kind) {
        if (roleId == null) {
            throw new IllegalArgumentException("角色ID不能为空");
        }
        return new AuthorizationChange(AuthorizationTargetType.ROLE, String.valueOf(roleId), kind);
    }

    public static AuthorizationChange tenant(String tenantId, AuthorizationChangeKind kind) {
        return new AuthorizationChange(AuthorizationTargetType.TENANT, tenantId, kind);
    }

}
