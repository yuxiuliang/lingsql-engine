package com.lingsql.tenant.model;

import lombok.Data;

import java.util.List;

/**
 * 租户通知通道风险项。
 *
 * @author 余炘洋
 * @date 2026-04-15
 */
@Data
public class TenantNotificationChannelIssueView {

    /**
     * 租户ID。
     *
     * @author 余炘洋
     * @date 2026-04-15
     */
    private String tenantId;

    /**
     * 租户名称。
     *
     * @author 余炘洋
     * @date 2026-04-15
     */
    private String tenantName;

    /**
     * 已选择的默认通知通道。
     *
     * @author 余炘洋
     * @date 2026-04-15
     */
    private List<String> selectedChannels;

    /**
     * 当前不可用的通知通道。
     *
     * @author 余炘洋
     * @date 2026-04-15
     */
    private List<String> unavailableChannels;

    /**
     * 风险说明。
     *
     * @author 余炘洋
     * @date 2026-04-15
     */
    private String reason;

    /**
     * 建议动作。
     *
     * @author 余炘洋
     * @date 2026-04-15
     */
    private String suggestion;
}
