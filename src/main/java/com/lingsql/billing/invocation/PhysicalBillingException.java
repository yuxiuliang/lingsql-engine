package com.lingsql.billing.invocation;

/**
 * 物理模型计费异常基类。
 *
 * @author 余炘洋
 * @date 2026-08-15
 */
public class PhysicalBillingException extends RuntimeException {

    public PhysicalBillingException(String message) {
        super(message);
    }

    public PhysicalBillingException(String message, Throwable cause) {
        super(message, cause);
    }
}
