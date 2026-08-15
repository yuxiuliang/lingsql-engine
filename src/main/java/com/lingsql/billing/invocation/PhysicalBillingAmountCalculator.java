package com.lingsql.billing.invocation;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 按预留时冻结的价格计算物理调用实际金额。
 *
 * @author 余炘洋
 * @date 2026-08-15
 */
final class PhysicalBillingAmountCalculator {

    private PhysicalBillingAmountCalculator() {
    }

    static long calculate(BillingInvocationEntity entity, int actualTokens) {
        try {
            return BigDecimal.valueOf(entity.getUnitCost())
                    .multiply(BigDecimal.valueOf(actualTokens))
                    .multiply(entity.getBillingWeight())
                    .setScale(0, RoundingMode.HALF_UP)
                    .longValueExact();
        } catch (ArithmeticException ex) {
            throw new BillingInvocationConflictException("实际模型费用超过系统上限");
        }
    }
}
