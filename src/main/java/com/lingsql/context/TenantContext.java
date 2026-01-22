package com.lingsql.context;

import lombok.extern.slf4j.Slf4j;

/**
 * 租户上下文管理器，使用 ThreadLocal 保存当前请求的租户 ID。
 *
 * @author 余炘洋
 * @date 2026-01-22
 */
@Slf4j
public final class TenantContext {

    /**
     * 保存租户 ID 的 ThreadLocal。
     *
     * @author 余炘洋
     * @date 2026-01-22
     */
    private static final ThreadLocal<String> CONTEXT = new ThreadLocal<>();

    /**
     * 私有构造函数。
     *
     * @author 余炘洋
     * @date 2026-01-22
     */
    private TenantContext() {
    }

    /**
     * 设置当前租户 ID。
     *
     * @param tenantId 租户标识
     * @author 余炘洋
     * @date 2026-01-22
     */
    public static void setTenantId(String tenantId) {
        log.debug("设置当前租户上下文: {}", tenantId);
        CONTEXT.set(tenantId);
    }

    /**
     * 获取当前租户 ID。
     *
     * @return 租户标识
     * @author 余炘洋
     * @date 2026-01-22
     */
    public static String getTenantId() {
        return CONTEXT.get();
    }

    /**
     * 清理当前线程上下文。
     *
     * @author 余炘洋
     * @date 2026-01-22
     */
    public static void clear() {
        CONTEXT.remove();
    }
}
