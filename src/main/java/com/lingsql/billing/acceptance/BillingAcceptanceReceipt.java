package com.lingsql.billing.acceptance;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 不可变计费验收回执。
 *
 * @param receiptId 回执ID
 * @param sessionId 验收会话ID
 * @param tenantId 租户ID
 * @param requestIds 已归一化的精确请求ID
 * @param requestIdsHash 请求集合SHA-256摘要
 * @param startLedgerWatermark 起始账本水位
 * @param endLedgerWatermark 结束账本水位
 * @param balanceBefore MySQL 起始余额
 * @param balanceAfter MySQL 结束余额
 * @param scopeNetAmount 本批请求净扣款
 * @param intervalNetAmount 整个水位区间净扣款
 * @param unrelatedEventCount 区间内非本批事件数
 * @param invocationCount 物理调用数
 * @param settledInvocationCount 已结算调用数
 * @param cancelledInvocationCount 已取消调用数
 * @param receiptHash 回执内容SHA-256摘要
 * @param closedBy 关闭操作人
 * @param closedAt 关闭时间
 * @author 余炘洋
 * @date 2026-08-15
 */
public record BillingAcceptanceReceipt(
        String receiptId,
        String sessionId,
        String tenantId,
        List<String> requestIds,
        String requestIdsHash,
        long startLedgerWatermark,
        long endLedgerWatermark,
        long balanceBefore,
        long balanceAfter,
        long scopeNetAmount,
        long intervalNetAmount,
        long unrelatedEventCount,
        long invocationCount,
        long settledInvocationCount,
        long cancelledInvocationCount,
        String receiptHash,
        String closedBy,
        LocalDateTime closedAt
) {
}
