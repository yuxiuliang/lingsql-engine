package com.lingsql.tenant.model;

import lombok.Data;

import java.util.List;

/**
 * 通知通道失败统计视图。
 *
 * @author 余炘洋
 * @date 2026-04-15
 */
@Data
public class TenantNotificationChannelFailureStatsView {

    /**
     * 统计租户ID。
     *
     * @author 余炘洋
     * @date 2026-04-15
     */
    private String tenantId;

    /**
     * 样本测试数。
     *
     * @author 余炘洋
     * @date 2026-04-15
     */
    private Integer totalTests;

    /**
     * 失败测试数。
     *
     * @author 余炘洋
     * @date 2026-04-15
     */
    private Integer failedTests;

    /**
     * 样本成功率。
     *
     * @author 余炘洋
     * @date 2026-04-15
     */
    private Double successRate;

    /**
     * 统计摘要。
     *
     * @author 余炘洋
     * @date 2026-04-15
     */
    private String summary;

    /**
     * 失败通道 Top 列表。
     *
     * @author 余炘洋
     * @date 2026-04-15
     */
    private List<TenantNotificationChannelFailureChannelView> topFailingChannels;

    /**
     * 失败原因 Top 列表。
     *
     * @author 余炘洋
     * @date 2026-04-15
     */
    private List<TenantNotificationChannelFailureReasonView> topFailureReasons;
}
