package com.lingsql.billing.model;

import lombok.Data;

/**
 * UNKNOWN 物理调用工作台查询条件。
 *
 * @author 余炘洋
 * @date 2026-10-17
 */
@Data
public class BillingUnknownInvocationQuery {

    /**
     * 租户标识。
     */
    private String tenantId;

    /**
     * 供应商名称。
     */
    private String providerName;
}
