package com.lingsql.billing.model;

import java.util.List;

/**
 * 单个工作流请求的计费与 SQL 审计验收证据。
 *
 * @param requestId 工作流请求ID
 * @param sqlAuditCount SQL审计数量
 * @param successfulSqlAuditCount 成功SQL审计数量
 * @param invocationCount 物理模型调用数量
 * @param architectSettledCount 已结算SQL架构师调用数量
 * @param analystSettledCount 已结算业务分析师调用数量
 * @param settledInvocationCount 已结算调用数量
 * @param unknownInvocationCount 未知调用数量
 * @param pendingInvocationCount 未终态调用数量
 * @param cancelledInvocationCount 已取消调用数量
 * @param identifiedInvocationCount 身份证据完整的物理调用数量
 * @param modelIdentities 逐物理调用实际模型身份摘要
 * @param ledgerInvocationCount 有账本的调用数量
 * @param eventCount 账本事件数量
 * @param reserveCount 预留事件数量
 * @param refundCount 退款事件数量
 * @param closeCount 关闭事件数量
 * @param reserveAmount 预留金额
 * @param refundAmount 退款金额
 * @param closeAmount 实扣金额
 * @param conservedInvocationCount 资金守恒调用数量
 * @param unbalancedInvocationCount 资金不守恒调用数量
 * @param complete 是否形成完整证据闭环
 * @param failureReasons 未通过原因
 * @author 余炘洋
 * @date 2026-08-15
 */
public record BillingRequestEvidenceView(
        String requestId,
        long sqlAuditCount,
        long successfulSqlAuditCount,
        long invocationCount,
        long architectSettledCount,
        long analystSettledCount,
        long settledInvocationCount,
        long unknownInvocationCount,
        long pendingInvocationCount,
        long cancelledInvocationCount,
        long identifiedInvocationCount,
        List<BillingInvocationIdentityView> modelIdentities,
        long ledgerInvocationCount,
        long eventCount,
        long reserveCount,
        long refundCount,
        long closeCount,
        long reserveAmount,
        long refundAmount,
        long closeAmount,
        long conservedInvocationCount,
        long unbalancedInvocationCount,
        boolean complete,
        List<String> failureReasons
) {
}
