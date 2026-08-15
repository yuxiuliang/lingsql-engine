package com.lingsql.billing.invocation;

import com.lingsql.common.exception.BizException;
import com.lingsql.common.exception.ErrorCode;

/**
 * 物理模型计费异常基类。
 *
 * @author 余炘洋
 * @date 2026-08-15
 */
public class PhysicalBillingException extends BizException {

    public PhysicalBillingException(String message) {
        this(ErrorCode.BILLING_INVALID, message);
    }

    public PhysicalBillingException(String message, Throwable cause) {
        this(ErrorCode.BILLING_INVALID, message, cause);
    }

    protected PhysicalBillingException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    protected PhysicalBillingException(ErrorCode errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
