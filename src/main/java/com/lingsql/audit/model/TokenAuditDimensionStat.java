package com.lingsql.audit.model;

import lombok.Data;

/**
 * Token 审计维度统计。
 *
 * @author 余炘洋
 * @date 2026-02-27
 */
@Data
public class TokenAuditDimensionStat {

    /**
     * 维度键（如模型名、租户ID）。
     *
     * @author 余炘洋
     * @date 2026-02-27
     */
    private String dimensionKey;

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

    /**
     * 平均 Token/请求。
     *
     * @author 余炘洋
     * @date 2026-02-27
     */
    private double avgTokensPerRequest;

    /**
     * 平均成本/请求。
     *
     * @author 余炘洋
     * @date 2026-02-27
     */
    private double avgCostPerRequest;
}
