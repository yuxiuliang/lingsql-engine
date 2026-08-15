package com.lingsql.billing.invocation;

/**
 * 已完成资金结算的物理模型响应。
 *
 * @param payload 响应载荷
 * @param invocationId 物理调用ID
 * @param reservationId 预留ID
 * @param actualTokens 实际Token
 * @param actualAmount 实际费用
 * @param billingVersion 计费版本
 * @param <T> 响应载荷类型
 * @author 余炘洋
 * @date 2026-08-15
 */
public record BilledModelResponse<T>(
        T payload,
        String invocationId,
        String reservationId,
        int actualTokens,
        long actualAmount,
        String billingVersion
) {
}
