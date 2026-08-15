package com.lingsql.billing.invocation;

import com.lingsql.common.exception.ErrorCode;

/**
 * 计费资金状态持久化异常。
 *
 * @author 余炘洋
 * @date 2026-08-15
 */
public class BillingPersistenceException extends PhysicalBillingException {

    public BillingPersistenceException(String message, Throwable cause) {
        super(ErrorCode.SYSTEM_ERROR, message, cause);
    }
}
