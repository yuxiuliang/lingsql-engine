package com.lingsql.billing.invocation;

/**
 * 余额不足导致的模型调用拒绝。
 *
 * @author 余炘洋
 * @date 2026-08-15
 */
public class BillingAccessDeniedException extends PhysicalBillingException {

    public BillingAccessDeniedException(String message) {
        super(message);
    }
}
