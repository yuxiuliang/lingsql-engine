package com.lingsql.billing.model;

/**
 * 供应商账单与平台物理调用的匹配状态。
 *
 * @author 余炘洋
 * @date 2026-10-17
 */
public enum VendorBillMatchState {

    /**
     * 账单未携带供应商请求标识，无法自动匹配。
     */
    NO_REFERENCE("账单未携带供应商请求标识，无法自动匹配"),

    /**
     * 未找到供应商请求标识对应的物理调用。
     */
    NOT_FOUND("未找到供应商请求标识对应的物理调用，需人工核对"),

    /**
     * 对应物理调用仍在预留或派发中。
     */
    PENDING("对应物理调用仍在预留或派发中，待终态后复核"),

    /**
     * 对应物理调用处于 UNKNOWN，账单可作为外部证据支撑裁决。
     */
    UNKNOWN("对应物理调用处于 UNKNOWN，可用该账单作为外部证据裁决"),

    /**
     * 账单与已结算物理调用的金额与 Token 一致。
     */
    MATCHED("账单与已结算物理调用一致"),

    /**
     * 账单与已结算物理调用的金额或 Token 不一致。
     */
    MISMATCH("账单与已结算物理调用的金额或 Token 不一致"),

    /**
     * 对应物理调用已确认未计费，账单仍计费。
     */
    CONFLICT("对应物理调用已确认未计费，账单仍计费，需人工核对");

    private final String description;

    VendorBillMatchState(String description) {
        this.description = description;
    }

    /**
     * 获取匹配状态说明。
     *
     * @return 状态说明
     * @author 余炘洋
     * @date 2026-10-17
     */
    public String getDescription() {
        return description;
    }
}
