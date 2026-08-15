package com.lingsql.billing.model;

/**
 * 指定请求集合的计费账本验收证据。
 *
 * @param requestedRequestCount 请求ID数量
 * @param matchedRequestCount 匹配到账本的请求数量
 * @param eventCount 账本事件数量
 * @param reserveCount RESERVE 事件数量
 * @param closeCount CLOSE 事件数量
 * @param reserveAmount 已应用预扣金额
 * @param invocationCount 物理模型调用数
 * @param matchedWorkflowCount 匹配物理调用的工作流请求数
 * @param settledInvocationCount 已结算物理调用数
 * @param unknownInvocationCount 未知物理调用数
 * @param pendingInvocationCount 未终态物理调用数
 * @author 余炘洋
 * @date 2026-08-15
 */
public record BillingLedgerEvidenceView(
        long requestedRequestCount,
        long matchedRequestCount,
        long eventCount,
        long reserveCount,
        long closeCount,
        long reserveAmount,
        long invocationCount,
        long matchedWorkflowCount,
        long settledInvocationCount,
        long unknownInvocationCount,
        long pendingInvocationCount
) {
}
