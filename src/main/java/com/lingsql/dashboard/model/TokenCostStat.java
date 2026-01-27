package com.lingsql.dashboard.model;

import lombok.Data;

/**
 * Token 成本对比统计。
 *
 * @author 余炘洋
 * @date 2026-01-27
 */
@Data
public class TokenCostStat {

    /**
     * 模型名称。
     *
     * @author 余炘洋
     * @date 2026-01-27
     */
    private String modelName;

    /**
     * Token 总量。
     *
     * @author 余炘洋
     * @date 2026-01-27
     */
    private Long totalTokens;

    /**
     * 成本总额。
     *
     * @author 余炘洋
     * @date 2026-01-27
     */
    private Long totalCost;

    /**
     * 调用次数。
     *
     * @author 余炘洋
     * @date 2026-01-27
     */
    private Long totalCount;
}
