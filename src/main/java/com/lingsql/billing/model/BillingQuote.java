package com.lingsql.billing.model;

import java.math.BigDecimal;

/**
 * 计费报价结果。
 *
 * @author 余炘洋
 * @date 2026-01-27
 */
public record BillingQuote(long costAmount,
                           long unitCost,
                           long tokenCount,
                           BigDecimal tenantWeight,
                           String policyVersion) {

    /**
     * 创建报价。
     *
     * @param costAmount    成本金额
     * @param unitCost      单价
     * @param tokenCount    Token 数量
     * @param tenantWeight  租户权重
     * @param policyVersion 计费版本
     * @return 报价
     * @author 余炘洋
     * @date 2026-01-27
     */
    public static BillingQuote of(long costAmount,
                                  long unitCost,
                                  long tokenCount,
                                  BigDecimal tenantWeight,
                                  String policyVersion) {
        return new BillingQuote(costAmount, unitCost, tokenCount, tenantWeight, policyVersion);
    }
}
