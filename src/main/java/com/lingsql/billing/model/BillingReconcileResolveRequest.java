package com.lingsql.billing.model;

import lombok.Data;

/**
 * 对账处理请求。
 *
 * @author 余炘洋
 * @date 2026-01-29
 */
@Data
public class BillingReconcileResolveRequest {

    /**
     * 处理备注。
     *
     * @author 余炘洋
     * @date 2026-01-29
     */
    private String note;
}
