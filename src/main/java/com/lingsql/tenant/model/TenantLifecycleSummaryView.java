package com.lingsql.tenant.model;

import lombok.Data;

/**
 * 租户生命周期汇总视图。
 *
 * @author 余炘洋
 * @date 2026-02-27
 */
@Data
public class TenantLifecycleSummaryView {

    /**
     * 总租户数。
     */
    private long total;

    /**
     * 正常租户数。
     */
    private long active;

    /**
     * 即将到期租户数。
     */
    private long expiringSoon;

    /**
     * 宽限期租户数。
     */
    private long gracePeriod;

    /**
     * 自动冻结租户数。
     */
    private long suspended;

    /**
     * 人工停用租户数。
     */
    private long disabled;
}

