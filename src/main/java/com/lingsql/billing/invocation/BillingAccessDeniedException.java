package com.lingsql.billing.invocation;

import com.lingsql.common.exception.ErrorCode;

/**
 * 余额不足导致的模型调用拒绝。
 *
 * @author 余炘洋
 * @date 2026-08-15
 */
public class BillingAccessDeniedException extends PhysicalBillingException {

    public BillingAccessDeniedException(String message) {
        super(ErrorCode.BILLING_BALANCE_INSUFFICIENT, message);
    }
}
