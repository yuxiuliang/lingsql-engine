package com.lingsql.schema.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 语义字典实体。
 *
 * @author 余炘洋
 * @date 2026-01-22
 */
@Data
@Entity
@Table(name = "t_lingsql_engine_semantic_lexicon")
public class SemanticLexiconItem {

    /**
     * 主键。
     *
     * @author 余炘洋
     * @date 2026-01-22
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 租户标识，NULL 表示全局配置。
     *
     * @author 余炘洋
     * @date 2026-01-22
     */
    @Column(name = "tenant_id")
    private String tenantId;

    /**
     * 术语名称。
     *
     * @author 余炘洋
     * @date 2026-01-22
     */
    @Column(name = "term", nullable = false)
    private String term;

    /**
     * 映射表达式。
     *
     * @author 余炘洋
     * @date 2026-01-22
     */
    @Column(name = "mapping", nullable = false)
    private String mapping;

    /**
     * 描述说明。
     *
     * @author 余炘洋
     * @date 2026-01-22
     */
    @Column(name = "description")
    private String description;

    /**
     * 状态：1 启用，0 禁用。
     *
     * @author 余炘洋
     * @date 2026-01-22
     */
    @Column(name = "status")
    private Integer status;

    /**
     * 创建时间。
     *
     * @author 余炘洋
     * @date 2026-01-22
     */
    @Column(name = "create_time")
    private LocalDateTime createTime;

    /**
     * 更新时间。
     *
     * @author 余炘洋
     * @date 2026-01-22
     */
    @Column(name = "update_time")
    private LocalDateTime updateTime;
}
