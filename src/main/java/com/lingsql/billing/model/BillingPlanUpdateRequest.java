package com.lingsql.billing.model;

import lombok.Data;

/**
 * 套餐更新请求。
 *
 * @author 余炘洋
 * @date 2026-01-29
 */
@Data
public class BillingPlanUpdateRequest {

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
}
