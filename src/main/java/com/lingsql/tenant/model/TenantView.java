package com.lingsql.tenant.model;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 租户视图。
 *
 * @author 余炘洋
 * @date 2026-01-26
 */
@Data
public class TenantView {

    /**
     * 主键ID。
     *
     * @author 余炘洋
     * @date 2026-01-26
     */
    private Long id;

    /**
     * 租户ID。
     *
     * @author 余炘洋
     * @date 2026-01-26
     */
    private String tenantId;

    /**
     * 租户名称。
     *
     * @author 余炘洋
     * @date 2026-01-26
     */
    private String tenantName;

    /**
     * 状态。
     *
     * @author 余炘洋
     * @date 2026-01-26
     */
    private Integer status;

    /**
     * 备注。
     *
     * @author 余炘洋
     * @date 2026-01-26
     */
    private String remark;

    /**
     * 联系人姓名。
     *
     * @author 余炘洋
     * @date 2026-01-26
     */
    private String contactName;

    /**
     * 联系电话。
     *
     * @author 余炘洋
     * @date 2026-01-26
     */
    private String contactPhone;

    /**
     * 联系邮箱。
     *
     * @author 余炘洋
     * @date 2026-01-26
     */
    private String contactEmail;

    /**
     * 默认通知通道列表。
     *
     * @author 余炘洋
     * @date 2026-04-15
     */
    private List<String> notificationChannels;

    /**
     * 到期时间。
     *
     * @author 余炘洋
     * @date 2026-01-26
     */
    private LocalDateTime expireTime;

    /**
     * 套餐名称。
     *
     * @author 余炘洋
     * @date 2026-01-26
     */
    private String planName;

    /**
     * 余额。
     *
     * @author 余炘洋
     * @date 2026-01-27
     */
    private Long quotaBalance;

    /**
     * 服务等级。
     *
     * @author 余炘洋
     * @date 2026-01-27
     */
    private String serviceLevel;

    /**
     * 租户类型。
     *
     * @author 余炘洋
     * @date 2026-01-27
     */
    private String tenantType;

    /**
     * 生命周期状态。
     *
     * @author 余炘洋
     * @date 2026-02-27
     */
    private String lifecycleState;

    /**
     * 距离到期天数（已到期为负数）。
     * 无到期时间时为 null。
     *
     * @author 余炘洋
     * @date 2026-02-27
     */
    private Long daysToExpire;

    /**
     * 宽限期剩余天数。
     *
     * @author 余炘洋
     * @date 2026-02-27
     */
    private Long graceDaysRemaining;

    /**
     * 流失风险分值（0-100）。
     *
     * @author 余炘洋
     * @date 2026-02-27
     */
    private Integer riskScore;

    /**
     * 流失风险等级。
     *
     * @author 余炘洋
     * @date 2026-02-27
     */
    private String riskLevel;

    /**
     * 风险原因列表。
     *
     * @author 余炘洋
     * @date 2026-02-27
     */
    private List<String> riskReasons;

    /**
     * 创建时间。
     *
     * @author 余炘洋
     * @date 2026-01-26
     */
    private LocalDateTime createTime;

    /**
     * 更新时间。
     *
     * @author 余炘洋
     * @date 2026-01-26
     */
    private LocalDateTime updateTime;

}
