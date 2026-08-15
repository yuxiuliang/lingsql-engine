package com.lingsql.billing.invocation;

/**
 * 物理模型调用资金状态。
 *
 * @author 余炘洋
 * @date 2026-08-15
 */
public enum BillingInvocationState {
    RESERVED,
    DISPATCHED,
    SETTLED,
    CANCELLED,
    UNKNOWN
}
