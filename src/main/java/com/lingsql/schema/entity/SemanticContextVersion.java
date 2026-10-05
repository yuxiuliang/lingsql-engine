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
 * 语义上下文版本快照：记录租户语义字典/Join 规则的版本与摘要，供提示词冻结引用。
 *
 * @author 余炘洋
 * @date 2026-10-05
 */
@Data
@Entity
@Table(name = "t_lingsql_engine_semantic_context_version")
public class SemanticContextVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId;

    @Column(name = "version_id", nullable = false, length = 64)
    private String versionId;

    @Column(name = "digest", length = 128)
    private String digest;

    @Column(name = "published_at", nullable = false)
    private LocalDateTime publishedAt;
}
