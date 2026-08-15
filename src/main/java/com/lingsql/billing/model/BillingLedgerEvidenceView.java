package com.lingsql.billing.model;

import java.util.List;

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
 * @param cancelledInvocationCount 已取消物理调用数
 * @param identifiedInvocationCount 身份证据完整的物理调用数
 * @param routeFingerprints 本批调用实际使用的路由配置指纹
 * @param sqlAuditCount SQL审计数量
 * @param successfulSqlAuditCount 成功SQL审计数量
 * @param refundCount 退款事件数量
 * @param refundAmount 退款金额
 * @param closeAmount 实扣金额
 * @param conservedInvocationCount 资金守恒调用数
 * @param unbalancedInvocationCount 资金不守恒调用数
 * @param completeRequestCount 完整请求数
 * @param conserved 是否整批守恒并完整
 * @param requests 逐请求证据
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
        long pendingInvocationCount,
        long cancelledInvocationCount,
        long identifiedInvocationCount,
        List<String> routeFingerprints,
        long sqlAuditCount,
        long successfulSqlAuditCount,
        long refundCount,
        long refundAmount,
        long closeAmount,
        long conservedInvocationCount,
        long unbalancedInvocationCount,
        long completeRequestCount,
        boolean conserved,
        List<BillingRequestEvidenceView> requests
) {
}
