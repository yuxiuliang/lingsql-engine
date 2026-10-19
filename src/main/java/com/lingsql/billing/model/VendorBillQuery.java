package com.lingsql.billing.model;

import lombok.Data;

/**
 * 供应商账单查询条件。
 *
 * @author 余炘洋
 * @date 2026-10-17
 */
@Data
public class VendorBillQuery {

    /**
     * 租户标识。
     */
    private String tenantId;

    /**
     * 供应商名称。
     */
    private String providerName;

    /**
     * 匹配状态。
     */
    private String matchState;

    /**
     * 外部账单标识。
     */
    private String externalBillId;
}
