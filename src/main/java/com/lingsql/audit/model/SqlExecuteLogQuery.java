package com.lingsql.audit.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * SQL 执行日志查询条件。
 *
 * @author 余炘洋
 * @date 2026-01-26
 */
@Data
public class SqlExecuteLogQuery {

    /**
     * 租户ID。
     *
     * @author 余炘洋
     * @date 2026-01-26
     */
    private String tenantId;

    /**
     * 执行人ID。
     *
     * @author 余炘洋
     * @date 2026-01-26
     */
    private Long userId;

    /**
     * 请求来源。
     *
     * @author 余炘洋
     * @date 2026-01-26
     */
    private String requestSource;

    /**
     * 问题关键词。
     *
     * @author 余炘洋
     * @date 2026-01-27
     */
    private String question;

    /**
     * 开始时间。
     *
     * @author 余炘洋
     * @date 2026-01-26
     */
    private LocalDateTime startTime;

    /**
     * 结束时间。
     *
     * @author 余炘洋
     * @date 2026-01-26
     */
    private LocalDateTime endTime;
}
