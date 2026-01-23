package com.lingsql.rbac.security;

/**
 * RBAC 安全常量。
 *
 * @author 余炘洋
 * @date 2026-01-23
 */
public final class RbacSecurityConstants {

    /**
     * Authorization 头。
     *
     * @author 余炘洋
     * @date 2026-01-23
     */
    public static final String AUTH_HEADER = "Authorization";

    /**
     * Bearer 前缀。
     *
     * @author 余炘洋
     * @date 2026-01-23
     */
    public static final String BEARER_PREFIX = "Bearer ";

    /**
     * Redis Token 前缀。
     *
     * @author 余炘洋
     * @date 2026-01-23
     */
    public static final String TOKEN_CACHE_PREFIX = "lingsql:token:";

    /**
     * Token 默认有效期(秒)。
     *
     * @author 余炘洋
     * @date 2026-01-23
     */
    public static final long DEFAULT_EXPIRE_SECONDS = 7200L;

    private RbacSecurityConstants() {
    }
}
