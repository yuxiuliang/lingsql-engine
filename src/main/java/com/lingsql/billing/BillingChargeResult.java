package com.lingsql.billing;

/**
 * 计费扣费结果。
 *
 * @author 余炘洋
 * @date 2026-01-27
 */
public record BillingChargeResult(boolean charged, long costAmount, long beforeBalance, long afterBalance) {

    /**
     * 扣费成功。
     *
     * @param costAmount    扣费金额
     * @param beforeBalance 扣费前余额
     * @param afterBalance  扣费后余额
     * @return 扣费结果
     * @author 余炘洋
     * @date 2026-01-27
     */
    public static BillingChargeResult success(long costAmount, long beforeBalance, long afterBalance) {
        return new BillingChargeResult(true, costAmount, beforeBalance, afterBalance);
    }

    /**
     * 跳过扣费。
     *
     * @return 扣费结果
     * @author 余炘洋
     * @date 2026-01-27
     */
    public static BillingChargeResult skip() {
        return new BillingChargeResult(false, 0, 0, 0);
    }
}
