package com.lingsql.billing.invocation;

/**
 * 物理模型响应及其可信用量。
 *
 * @param payload 响应载荷
 * @param actualTokens 实际Token，null表示供应商未返回可信用量
 * @param providerRequestId 供应商请求ID
 * @param <T> 响应载荷类型
 * @author 余炘洋
 * @date 2026-08-15
 */
public record PhysicalModelResponse<T>(
        T payload,
        Integer actualTokens,
        String providerRequestId
) {
}
