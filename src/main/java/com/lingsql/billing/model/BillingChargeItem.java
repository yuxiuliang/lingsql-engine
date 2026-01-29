package com.lingsql.billing.model;

/**
 * 计费扣费明细。
 *
 * @author 余炘洋
 * @date 2026-01-29
 */
public record BillingChargeItem(String modelName, int tokenCount, long unitCost, long costAmount, String policyVersion) {
}
