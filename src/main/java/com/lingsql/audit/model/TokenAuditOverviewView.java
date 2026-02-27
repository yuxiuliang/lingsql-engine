package com.lingsql.audit.model;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Token 审计总览视图。
 *
 * @author 余炘洋
 * @date 2026-02-27
 */
@Data
public class TokenAuditOverviewView {

    /**
     * 统计开始时间。
     *
     * @author 余炘洋
     * @date 2026-02-27
     */
    private LocalDateTime startTime;

    /**
     * 统计结束时间。
     *
     * @author 余炘洋
     * @date 2026-02-27
     */
    private LocalDateTime endTime;

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

    /**
     * 模型消耗排行。
     *
     * @author 余炘洋
     * @date 2026-02-27
     */
    private List<TokenAuditDimensionStat> modelStats;

    /**
     * 租户消耗排行。
     *
     * @author 余炘洋
     * @date 2026-02-27
     */
    private List<TokenAuditDimensionStat> tenantStats;

    /**
     * 日趋势。
     *
     * @author 余炘洋
     * @date 2026-02-27
     */
    private List<TokenAuditTrendPoint> trend;
}
