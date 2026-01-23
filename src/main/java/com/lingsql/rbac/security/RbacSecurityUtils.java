package com.lingsql.rbac.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * RBAC 安全上下文工具。
 *
 * @author 余炘洋
 * @date 2026-01-23
 */
public final class RbacSecurityUtils {

    private RbacSecurityUtils() {
    }

    /**
     * 获取当前会话。
     *
     * @return 登录会话
     * @author 余炘洋
     * @date 2026-01-23
     */
    public static RbacUserSession currentSession() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getPrincipal() == null) {
            return null;
        }
        if (authentication.getPrincipal() instanceof RbacUserSession session) {
            return session;
        }
        return null;
    }
}
