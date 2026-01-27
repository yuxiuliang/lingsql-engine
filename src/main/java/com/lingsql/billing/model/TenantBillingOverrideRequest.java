package com.lingsql.billing.model;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 租户计费覆盖请求。
 *
 * @author 余炘洋
 * @date 2026-01-27
 */
@Data
public class TenantBillingOverrideRequest {

    /**
     * 加价倍率。
     *
     * @author 余炘洋
     * @date 2026-01-27
     */
    private BigDecimal priceMultiplier;

    /**
     * 状态: 1-生效, 0-停用。
     *
     * @author 余炘洋
     * @date 2026-01-27
     */
    private Integer status;

    /**
     * 备注。
     *
     * @author 余炘洋
     * @date 2026-01-27
     */
    private String remark;
}
