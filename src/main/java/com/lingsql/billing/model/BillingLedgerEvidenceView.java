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
 * @author 余炘洋
 * @date 2026-08-15
 */
public record BillingLedgerEvidenceView(
        long requestedRequestCount,
        long matchedRequestCount,
        long eventCount,
        long reserveCount,
        long closeCount,
        long reserveAmount
) {
}
