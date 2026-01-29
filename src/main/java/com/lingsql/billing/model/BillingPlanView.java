package com.lingsql.billing.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 套餐视图。
 *
 * @author 余炘洋
 * @date 2026-01-29
 */
@Data
public class BillingPlanView {

    /**
     * 主键ID。
     *
     * @author 余炘洋
     * @date 2026-01-29
     */
    private Long id;

    /**
     * 套餐编码。
     *
     * @author 余炘洋
     * @date 2026-01-29
     */
    private String planCode;

    /**
     * 套餐名称。
     *
     * @author 余炘洋
     * @date 2026-01-29
     */
    private String planName;

    /**
     * 状态: 1-启用, 0-停用。
     *
     * @author 余炘洋
     * @date 2026-01-29
     */
    private Integer status;

    /**
     * 服务等级。
     *
     * @author 余炘洋
     * @date 2026-01-29
     */
    private String serviceLevel;

    /**
     * 租户类型。
     *
     * @author 余炘洋
     * @date 2026-01-29
     */
    private String tenantType;

    /**
     * 默认余额。
     *
     * @author 余炘洋
     * @date 2026-01-29
     */
    private Long defaultQuotaBalance;

    /**
     * 备注。
     *
     * @author 余炘洋
     * @date 2026-01-29
     */
    private String remark;

    /**
     * 创建时间。
     *
     * @author 余炘洋
     * @date 2026-01-29
     */
    private LocalDateTime createTime;

    /**
     * 更新时间。
     *
     * @author 余炘洋
     * @date 2026-01-29
     */
    private LocalDateTime updateTime;
}
