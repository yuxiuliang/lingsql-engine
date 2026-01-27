package com.lingsql.dashboard.model;

import lombok.Data;

import java.util.List;

/**
 * 老板仪表盘视图。
 *
 * @author 余炘洋
 * @date 2026-01-27
 */
@Data
public class ProfitDashboardView {

    /**
     * 活跃租户数。
     *
     * @author 余炘洋
     * @date 2026-01-27
     */
    private Long activeTenantCount;

    /**
     * Top 问题统计。
     *
     * @author 余炘洋
     * @date 2026-01-27
     */
    private List<TopQuestionStat> topQuestions;

    /**
     * 准确率趋势。
     *
     * @author 余炘洋
     * @date 2026-01-27
     */
    private List<AccuracyTrendPoint> accuracyTrend;

    /**
     * Token 成本对比。
     *
     * @author 余炘洋
     * @date 2026-01-27
     */
    private List<TokenCostStat> tokenCostStats;
}
