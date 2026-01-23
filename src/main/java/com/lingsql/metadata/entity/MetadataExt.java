package com.lingsql.metadata.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 元数据扩展字典实体。
 *
 * @author 余炘洋
 * @date 2026-01-23
 */
@Data
@Entity
@Table(name = "t_lingsql_engine_metadata_ext")
public class MetadataExt {

    /**
     * 主键。
     *
     * @author 余炘洋
     * @date 2026-01-23
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 租户标识。
     *
     * @author 余炘洋
     * @date 2026-01-23
     */
    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    /**
     * 数据库名称。
     *
     * @author 余炘洋
     * @date 2026-01-23
     */
    @Column(name = "schema_name")
    private String schemaName;

    /**
     * 表名。
     *
     * @author 余炘洋
     * @date 2026-01-23
     */
    @Column(name = "table_name", nullable = false)
    private String tableName;

    /**
     * 字段名。
     *
     * @author 余炘洋
     * @date 2026-01-23
     */
    @Column(name = "column_name", nullable = false)
    private String columnName;

    /**
     * 业务注释。
     *
     * @author 余炘洋
     * @date 2026-01-23
     */
    @Column(name = "business_comment", columnDefinition = "TEXT")
    private String businessComment;

    /**
     * 是否隔离字段：1 是，0 否。
     *
     * @author 余炘洋
     * @date 2026-01-23
     */
    @Column(name = "is_isolated_field")
    private Integer isIsolatedField;

    /**
     * 创建时间。
     *
     * @author 余炘洋
     * @date 2026-01-23
     */
    @Column(name = "create_time")
    private LocalDateTime createTime;

    /**
     * 更新时间。
     *
     * @author 余炘洋
     * @date 2026-01-23
     */
    @Column(name = "update_time")
    private LocalDateTime updateTime;
}

