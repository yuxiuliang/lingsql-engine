package com.lingsql.audit.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 审计日志查询条件。
 *
 * @author 余炘洋
 * @date 2026-01-26
 */
@Data
public class AuditLogQuery {

    /**
     * 租户ID。
     *
     * @author 余炘洋
     * @date 2026-01-26
     */
    private String tenantId;

    /**
     * 用户ID。
     *
     * @author 余炘洋
     * @date 2026-01-26
     */
    private Long userId;

    /**
     * 动作关键字。
     *
     * @author 余炘洋
     * @date 2026-01-26
     */
    private String action;

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
