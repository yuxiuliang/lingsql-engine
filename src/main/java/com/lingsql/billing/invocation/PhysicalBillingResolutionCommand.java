package com.lingsql.billing.invocation;

/**
 * UNKNOWN 物理调用资金裁决命令。
 *
 * @param invocationId 物理调用ID
 * @param operator 操作人
 * @param evidenceHash 外部证据的 SHA-256 摘要
 * @param providerRequestId 供应商请求ID
 * @param resolutionType 裁决类型
 * @param actualTokens 已确认的实际 Token 数
 * @author 余炘洋
 * @date 2026-08-15
 */
public record PhysicalBillingResolutionCommand(
        String invocationId,
        String operator,
        String evidenceHash,
        String providerRequestId,
        PhysicalBillingResolutionType resolutionType,
        Integer actualTokens
) {
}
