package com.lingsql.audit.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * Token 审计查询条件。
 *
 * @author 余炘洋
 * @date 2026-02-27
 */
@Data
public class TokenAuditQuery {

    /**
     * 租户ID。
     *
     * @author 余炘洋
     * @date 2026-02-27
     */
    private String tenantId;

    /**
     * 用户ID。
     *
     * @author 余炘洋
     * @date 2026-02-27
     */
    private Long userId;

    /**
     * 问题关键词。
     *
     * @author 余炘洋
     * @date 2026-02-27
     */
    private String question;

    /**
     * 请求来源。
     *
     * @author 余炘洋
     * @date 2026-02-27
     */
    private String requestSource;

    /**
     * 开始时间。
     *
     * @author 余炘洋
     * @date 2026-02-27
     */
    private LocalDateTime startTime;

    /**
     * 结束时间。
     *
     * @author 余炘洋
     * @date 2026-02-27
     */
    private LocalDateTime endTime;

    /**
     * TopN 数量。
     *
     * @author 余炘洋
     * @date 2026-02-27
     */
    private Integer topN;
}
