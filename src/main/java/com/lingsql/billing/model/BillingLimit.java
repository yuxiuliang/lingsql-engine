package com.lingsql.billing.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 服务等级限额配置。
 *
 * @author 余炘洋
 * @date 2026-01-27
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BillingLimit {

    /**
     * 每日调用上限。
     *
     * @author 余炘洋
     * @date 2026-01-27
     */
    private int dailyCallLimit;

    /**
     * 每日 Token 上限。
     *
     * @author 余炘洋
     * @date 2026-01-27
     */
    private long dailyTokenLimit;
}
