package com.lingsql.billing.model;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 套餐创建请求。
 *
 * @author 余炘洋
 * @date 2026-01-29
 */
@Data
public class BillingPlanCreateRequest {

    /**
     * 套餐编码（可为空，系统自动生成）。
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
    @NotBlank(message = "套餐名称不能为空")
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
