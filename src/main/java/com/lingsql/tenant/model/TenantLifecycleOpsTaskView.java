package com.lingsql.tenant.model;

import lombok.Data;

/**
 * 租户生命周期运营任务视图。
 *
 * @author 余炘洋
 * @date 2026-02-27
 */
@Data
public class TenantLifecycleOpsTaskView {

    /**
     * 租户ID。
     */
    private String tenantId;

    /**
     * 租户名称。
     */
    private String tenantName;

    /**
     * 生命周期状态。
     */
    private String lifecycleState;

    /**
     * 风险等级。
     */
    private String riskLevel;

    /**
     * 风险分值（0-100）。
     */
    private Integer riskScore;

    /**
     * 距离到期天数。
     */
    private Long daysToExpire;

    /**
     * 任务优先级：P0/P1/P2。
     */
    private String priority;

    /**
     * 建议动作。
     */
    private String suggestedAction;

    /**
     * 触发原因。
     */
    private String triggerReason;

    /**
     * 处理 SLA（小时）。
     */
    private Integer slaHours;

    /**
     * 联系人姓名。
     */
    private String contactName;

    /**
     * 联系电话。
     */
    private String contactPhone;

    /**
     * 联系邮箱。
     */
    private String contactEmail;
}

