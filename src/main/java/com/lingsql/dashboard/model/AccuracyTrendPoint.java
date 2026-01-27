package com.lingsql.dashboard.model;

import lombok.Data;

import java.time.LocalDate;

/**
 * 准确率趋势点。
 *
 * @author 余炘洋
 * @date 2026-01-27
 */
@Data
public class AccuracyTrendPoint {

    /**
     * 日期。
     *
     * @author 余炘洋
     * @date 2026-01-27
     */
    private LocalDate date;

    /**
     * 准确率(百分比)。
     *
     * @author 余炘洋
     * @date 2026-01-27
     */
    private Double accuracy;

    /**
     * 评价总数。
     *
     * @author 余炘洋
     * @date 2026-01-27
     */
    private Long totalCount;

    /**
     * 好评数量。
     *
     * @author 余炘洋
     * @date 2026-01-27
     */
    private Long positiveCount;
}
