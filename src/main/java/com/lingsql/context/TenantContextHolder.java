package com.lingsql.context;

import lombok.extern.slf4j.Slf4j;

/**
 * 兼容旧代码的租户上下文持有者，内部委托给 TenantContext。
 *
 * @author 余炘洋
 * @date 2026-01-22
 */
@Slf4j
public class TenantContextHolder {

    /**
     * 设置当前租户 ID。
     *
     * @param tenantId 租户标识
     * @author 余炘洋
     * @date 2026-01-22
     */
    public static void setTenantId(String tenantId) {
        log.debug("设置当前租户上下文: {}", tenantId);
        TenantContext.setTenantId(tenantId);
    }

    /**
     * 获取当前租户 ID。
     *
     * @return 租户标识
     * @author 余炘洋
     * @date 2026-01-22
     */
    public static String getTenantId() {
        return TenantContext.getTenantId();
    }

    /**
     * 清除上下文。
     *
     * @author 余炘洋
     * @date 2026-01-22
     */
    public static void clear() {
        TenantContext.clear();
    }
}
