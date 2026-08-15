package com.lingsql.tenant.model;

import lombok.Data;

import java.util.List;

/**
 * 租户通知通道健康视图。
 *
 * @author 余炘洋
 * @date 2026-04-15
 */
@Data
public class TenantNotificationChannelHealthView {

    /**
     * 系统通知总开关是否启用。
     *
     * @author 余炘洋
     * @date 2026-04-15
     */
    private Boolean alertEnabled;

    /**
     * 当前已配置的系统通知通道。
     *
     * @author 余炘洋
     * @date 2026-04-15
     */
    private List<String> configuredChannels;

    /**
     * 当前未配置的系统通知通道。
     *
     * @author 余炘洋
     * @date 2026-04-15
     */
    private List<String> unconfiguredChannels;

    /**
     * 已配置默认通知通道的租户数量。
     *
     * @author 余炘洋
     * @date 2026-04-15
     */
    private Integer tenantUsingNotificationChannels;

    /**
     * 存在风险的租户数量。
     *
     * @author 余炘洋
     * @date 2026-04-15
     */
    private Integer tenantIssueCount;

    /**
     * 总结说明。
     *
     * @author 余炘洋
     * @date 2026-04-15
     */
    private String summary;

    /**
     * 风险租户清单。
     *
     * @author 余炘洋
     * @date 2026-04-15
     */
    private List<TenantNotificationChannelIssueView> issues;
}
