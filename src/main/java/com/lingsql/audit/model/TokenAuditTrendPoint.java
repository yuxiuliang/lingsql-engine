package com.lingsql.audit.model;

import lombok.Data;

import java.time.LocalDate;

/**
 * Token 审计趋势点。
 *
 * @author 余炘洋
 * @date 2026-02-27
 */
@Data
public class TokenAuditTrendPoint {

    /**
     * 统计日期。
     *
     * @author 余炘洋
     * @date 2026-02-27
     */
    private LocalDate statDate;

    /**
     * 请求总数。
     *
     * @author 余炘洋
     * @date 2026-02-27
     */
    private long totalCount;

    /**
     * 成功请求数。
     *
     * @author 余炘洋
     * @date 2026-02-27
     */
    private long successCount;

    /**
     * 失败请求数。
     *
     * @author 余炘洋
     * @date 2026-02-27
     */
    private long failureCount;

    /**
     * 成功率（百分比）。
     *
     * @author 余炘洋
     * @date 2026-02-27
     */
    private double successRate;

    /**
     * Token 总量。
     *
     * @author 余炘洋
     * @date 2026-02-27
     */
    private long totalTokens;

    /**
     * 成本总额。
     *
     * @author 余炘洋
     * @date 2026-02-27
     */
    private long totalCost;
}
