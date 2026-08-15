package com.lingsql.billing.invocation;

import java.time.LocalDateTime;

/**
 * UNKNOWN 物理调用资金裁决回执。
 *
 * @param resolutionId 裁决ID
 * @param invocationId 物理调用ID
 * @param tenantId 租户ID
 * @param resolutionType 裁决类型
 * @param state 调用终态
 * @param actualTokens 实际 Token 数
 * @param actualAmount 实际金额
 * @param refundAmount 退款金额
 * @param providerRequestId 供应商请求ID
 * @param evidenceHash 外部证据摘要
 * @param operator 操作人
 * @param resolvedAt 裁决时间
 * @author 余炘洋
 * @date 2026-08-15
 */
public record PhysicalBillingResolutionReceipt(
        String resolutionId,
        String invocationId,
        String tenantId,
        PhysicalBillingResolutionType resolutionType,
        BillingInvocationState state,
        Integer actualTokens,
        long actualAmount,
        long refundAmount,
        String providerRequestId,
        String evidenceHash,
        String operator,
        LocalDateTime resolvedAt
) {
}
