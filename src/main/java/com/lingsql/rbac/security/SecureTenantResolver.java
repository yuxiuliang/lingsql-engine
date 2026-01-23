package com.lingsql.rbac.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.util.StringUtils;

import java.util.Set;

/**
 * 租户安全解析器。
 *
 * @author 余炘洋
 * @date 2026-01-23
 */
public final class SecureTenantResolver {

    private static final Logger log = LoggerFactory.getLogger(SecureTenantResolver.class);
    private static final Set<String> SYS_ADMIN_ROLES = Set.of("SYS_ADMIN", "SUPER_ADMIN");

    private SecureTenantResolver() {
    }

    /**
     * 安全解析租户ID。
     *
     * @param inputTenantId 接口传入的租户ID
     * @return 安全租户ID
     * @author 余炘洋
     * @date 2026-01-23
     */
    public static String resolveSecureTenantId(String inputTenantId) {
        RbacUserSession session = RbacSecurityUtils.currentSession();
        if (session == null) {
            throw new BadCredentialsException("未登录或登录已过期");
        }
        String sessionTenantId = session.getTenantId();
        boolean sysAdmin = isSysAdmin(session);
        if (sysAdmin) {
            if (StringUtils.hasText(inputTenantId)) {
                return inputTenantId.trim();
            }
            return sessionTenantId;
        }
        if (StringUtils.hasText(inputTenantId)
                && StringUtils.hasText(sessionTenantId)
                && !sessionTenantId.equalsIgnoreCase(inputTenantId.trim())) {
            log.warn("越权租户访问拦截: userId={}, username={}, sessionTenantId={}, inputTenantId={}",
                    session.getUserId(), session.getUsername(), sessionTenantId, inputTenantId);
            throw new AccessDeniedException("无权限访问");
        }
        return sessionTenantId;
    }

    /**
     * 判断是否为系统管理员角色。
     *
     * @param session 登录会话
     * @return 是否系统管理员
     * @author 余炘洋
     * @date 2026-01-23
     */
    public static boolean isSysAdmin(RbacUserSession session) {
        if (session == null || session.getRoles() == null) {
            return false;
        }
        return session.getRoles().stream()
                .filter(StringUtils::hasText)
                .anyMatch(role -> SYS_ADMIN_ROLES.contains(role.trim().toUpperCase()));
    }
}
