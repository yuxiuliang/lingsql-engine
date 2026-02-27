package com.lingsql.tenant.model;

/**
 * 租户生命周期快照。
 *
 * @param state              生命周期状态
 * @param daysToExpire       距离到期天数（已到期为负数）
 * @param graceDaysRemaining 宽限期剩余天数（非宽限期返回 0）
 * @author 余炘洋
 * @date 2026-02-27
 */
public record TenantLifecycleSnapshot(
        TenantLifecycleState state,
        long daysToExpire,
        long graceDaysRemaining
) {

    /**
     * 是否允许继续访问核心能力。
     *
     * @return true-允许访问；false-禁止访问
     * @author 余炘洋
     * @date 2026-02-27
     */
    public boolean accessAllowed() {
        return state == TenantLifecycleState.ACTIVE
                || state == TenantLifecycleState.EXPIRING_SOON
                || state == TenantLifecycleState.GRACE_PERIOD;
    }
}

