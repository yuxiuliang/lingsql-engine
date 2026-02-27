package com.lingsql.tenant.model;

/**
 * 租户生命周期状态。
 *
 * @author 余炘洋
 * @date 2026-02-27
 */
public enum TenantLifecycleState {
    /**
     * 正常服务中。
     */
    ACTIVE,
    /**
     * 即将到期。
     */
    EXPIRING_SOON,
    /**
     * 到期宽限期内。
     */
    GRACE_PERIOD,
    /**
     * 因到期超过宽限期被系统冻结。
     */
    SUSPENDED,
    /**
     * 人工停用。
     */
    DISABLED
}

