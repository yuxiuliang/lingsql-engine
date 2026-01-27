package com.lingsql.billing;

/**
 * 计费校验结果。
 *
 * @author 余炘洋
 * @date 2026-01-27
 */
public record BillingCheckResult(boolean allowed, String message) {

    /**
     * 允许通过。
     *
     * @return 结果
     * @author 余炘洋
     * @date 2026-01-27
     */
    public static BillingCheckResult allow() {
        return new BillingCheckResult(true, null);
    }

    /**
     * 拒绝通过。
     *
     * @param message 提示语
     * @return 结果
     * @author 余炘洋
     * @date 2026-01-27
     */
    public static BillingCheckResult deny(String message) {
        return new BillingCheckResult(false, message);
    }
}
