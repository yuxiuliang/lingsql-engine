package com.lingsql.audit.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * SQL 执行日志视图。
 *
 * @author 余炘洋
 * @date 2026-01-26
 */
@Data
public class SqlExecuteLogView {

    /**
     * ID。
     *
     * @author 余炘洋
     * @date 2026-01-26
     */
    private Long id;

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
     * 原始问题。
     *
     * @author 余炘洋
     * @date 2026-01-26
     */
    private String question;

    /**
     * AI 生成 SQL。
     *
     * @author 余炘洋
     * @date 2026-01-26
     */
    private String aiSql;

    /**
     * 执行 SQL。
     *
     * @author 余炘洋
     * @date 2026-01-26
     */
    private String executedSql;

    /**
     * 是否成功。
     *
     * @author 余炘洋
     * @date 2026-01-26
     */
    private Integer success;

    /**
     * 错误信息。
     *
     * @author 余炘洋
     * @date 2026-01-26
     */
    private String errorMessage;

    /**
     * 模型供应商。
     *
     * @author 余炘洋
     * @date 2026-01-26
     */
    private String modelProvider;

    /**
     * 模型名称。
     *
     * @author 余炘洋
     * @date 2026-01-26
     */
    private String modelName;

    /**
     * 模型版本。
     *
     * @author 余炘洋
     * @date 2026-01-26
     */
    private String modelVersion;

    /**
     * Token 数量。
     *
     * @author 余炘洋
     * @date 2026-01-27
     */
    private Integer tokenCount;

    /**
     * 成本金额。
     *
     * @author 余炘洋
     * @date 2026-01-27
     */
    private Long costAmount;

    /**
     * 计费版本。
     *
     * @author 余炘洋
     * @date 2026-01-27
     */
    private String billingVersion;

    /**
     * 执行耗时(毫秒)。
     *
     * @author 余炘洋
     * @date 2026-01-26
     */
    private Long executeCostMs;

    /**
     * 返回行数。
     *
     * @author 余炘洋
     * @date 2026-01-26
     */
    private Integer resultRows;

    /**
     * 请求来源。
     *
     * @author 余炘洋
     * @date 2026-01-26
     */
    private String requestSource;

    /**
     * 评分。
     *
     * @author 余炘洋
     * @date 2026-01-27
     */
    private Integer rating;

    /**
     * 评价内容。
     *
     * @author 余炘洋
     * @date 2026-01-27
     */
    private String comment;

    /**
     * 是否慢查询。
     *
     * @author 余炘洋
     * @date 2026-01-27
     */
    private Boolean slow;

    /**
     * 创建时间。
     *
     * @author 余炘洋
     * @date 2026-01-26
     */
    private LocalDateTime createTime;
}
