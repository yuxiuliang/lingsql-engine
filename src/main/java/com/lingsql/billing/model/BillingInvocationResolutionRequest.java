package com.lingsql.billing.model;

import com.lingsql.billing.invocation.PhysicalBillingResolutionType;

/**
 * 物理调用资金裁决管理请求。
 *
 * @param evidenceHash 外部证据的 SHA-256 摘要
 * @param providerRequestId 供应商请求ID
 * @param resolutionType 裁决类型
 * @param actualTokens 已确认的实际 Token 数
 * @author 余炘洋
 * @date 2026-08-15
 */
public record BillingInvocationResolutionRequest(
        String evidenceHash,
        String providerRequestId,
        PhysicalBillingResolutionType resolutionType,
        Integer actualTokens
) {
}
