package com.lingsql.billing.invocation;

/**
 * 已发出模型请求但无法确认最终费用的异常。
 *
 * @author 余炘洋
 * @date 2026-08-15
 */
public class BillingInvocationUnknownException extends PhysicalBillingException {

    public BillingInvocationUnknownException(String message) {
        super(message);
    }

    public BillingInvocationUnknownException(String message, Throwable cause) {
        super(message, cause);
    }
}
