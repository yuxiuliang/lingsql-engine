package com.lingsql.billing.acceptance;

import java.time.LocalDateTime;

/**
 * 计费验收会话开启回执。
 *
 * @param sessionId 会话ID
 * @param tenantId 租户ID
 * @param balanceBefore MySQL 起始余额
 * @param ledgerWatermark 起始账本水位
 * @param openedBy 操作人
 * @param openedAt 开启时间
 * @author 余炘洋
 * @date 2026-08-15
 */
public record BillingAcceptanceSessionReceipt(
        String sessionId,
        String tenantId,
        long balanceBefore,
        long ledgerWatermark,
        String openedBy,
        LocalDateTime openedAt
) {
}
