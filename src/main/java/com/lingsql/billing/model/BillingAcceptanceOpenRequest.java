package com.lingsql.billing.model;

/**
 * 开启计费验收会话请求。
 *
 * @param tenantId 试点租户ID
 * @author 余炘洋
 * @date 2026-08-15
 */
public record BillingAcceptanceOpenRequest(String tenantId) {
}
