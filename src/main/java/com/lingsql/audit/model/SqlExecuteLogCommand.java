package com.lingsql.audit.model;

import lombok.Data;

/**
 * SQL 执行日志写入请求。
 *
 * @author 余炘洋
 * @date 2026-01-26
 */
@Data
public class SqlExecuteLogCommand {

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
    private boolean success;

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
}
