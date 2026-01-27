package com.lingsql.dashboard.model;

import lombok.Data;

/**
 * 问题耗能统计。
 *
 * @author 余炘洋
 * @date 2026-01-27
 */
@Data
public class TopQuestionStat {

    /**
     * 问题内容。
     *
     * @author 余炘洋
     * @date 2026-01-27
     */
    private String question;

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
