package com.lingsql.audit.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * SQL 执行审计日志实体。
 *
 * @author 余炘洋
 * @date 2026-01-26
 */
@Data
@Entity
@Table(name = "t_lingsql_engine_sql_execute_log")
public class SqlExecuteLog {

    /**
     * 主键ID。
     *
     * @author 余炘洋
     * @date 2026-01-26
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 租户ID。
     *
     * @author 余炘洋
     * @date 2026-01-26
     */
    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    /**
     * 执行人ID。
     *
     * @author 余炘洋
     * @date 2026-01-26
     */
    @Column(name = "user_id")
    private Long userId;

    /**
     * 原始问题。
     *
     * @author 余炘洋
     * @date 2026-01-26
     */
    @Column(name = "question", columnDefinition = "TEXT")
    private String question;

    /**
     * AI 生成 SQL。
     *
     * @author 余炘洋
     * @date 2026-01-26
     */
    @Column(name = "ai_sql", columnDefinition = "TEXT")
    private String aiSql;

    /**
     * 执行 SQL。
     *
     * @author 余炘洋
     * @date 2026-01-26
     */
    @Column(name = "executed_sql", columnDefinition = "TEXT")
    private String executedSql;

    /**
     * 是否成功: 1-成功, 0-失败。
     *
     * @author 余炘洋
     * @date 2026-01-26
     */
    @Column(name = "success")
    private Integer success;

    /**
     * 错误信息。
     *
     * @author 余炘洋
     * @date 2026-01-26
     */
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    /**
     * 模型供应商。
     *
     * @author 余炘洋
     * @date 2026-01-26
     */
    @Column(name = "model_provider")
    private String modelProvider;

    /**
     * 模型名称。
     *
     * @author 余炘洋
     * @date 2026-01-26
     */
    @Column(name = "model_name")
    private String modelName;

    /**
     * 模型版本。
     *
     * @author 余炘洋
     * @date 2026-01-26
     */
    @Column(name = "model_version")
    private String modelVersion;

    /**
     * 执行耗时(毫秒)。
     *
     * @author 余炘洋
     * @date 2026-01-26
     */
    @Column(name = "execute_cost_ms")
    private Long executeCostMs;

    /**
     * 返回行数。
     *
     * @author 余炘洋
     * @date 2026-01-26
     */
    @Column(name = "result_rows")
    private Integer resultRows;

    /**
     * 请求来源。
     *
     * @author 余炘洋
     * @date 2026-01-26
     */
    @Column(name = "request_source")
    private String requestSource;

    /**
     * 创建时间。
     *
     * @author 余炘洋
     * @date 2026-01-26
     */
    @Column(name = "create_time")
    private LocalDateTime createTime;
}
