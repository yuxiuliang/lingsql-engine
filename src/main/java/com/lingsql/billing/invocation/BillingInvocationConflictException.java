package com.lingsql.billing.invocation;

import com.lingsql.common.exception.ErrorCode;

/**
 * 物理调用幂等冲突或状态冲突。
 *
 * @author 余炘洋
 * @date 2026-08-15
 */
public class BillingInvocationConflictException extends PhysicalBillingException {

    public BillingInvocationConflictException(String message) {
        super(ErrorCode.CONFLICT, message);
    }
}
