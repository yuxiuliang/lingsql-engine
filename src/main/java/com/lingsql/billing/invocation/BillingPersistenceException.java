package com.lingsql.billing.invocation;

/**
 * 计费资金状态持久化异常。
 *
 * @author 余炘洋
 * @date 2026-08-15
 */
public class BillingPersistenceException extends PhysicalBillingException {

    public BillingPersistenceException(String message, Throwable cause) {
        super(message, cause);
    }
}
