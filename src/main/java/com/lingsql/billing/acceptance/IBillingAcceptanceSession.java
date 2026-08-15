package com.lingsql.billing.acceptance;

import java.util.List;

/**
 * 计费验收会话边界。
 *
 * @author 余炘洋
 * @date 2026-08-15
 */
public interface IBillingAcceptanceSession {

    BillingAcceptanceSessionReceipt open(String tenantId, String operator);

    BillingAcceptanceReceipt close(String sessionId, List<String> requestIds, String operator);
}
