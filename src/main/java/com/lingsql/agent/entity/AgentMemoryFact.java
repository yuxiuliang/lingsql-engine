package com.lingsql.agent.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Agent 长期记忆事实：程序记忆为租户级已验证口径，永久记忆为用户级跨会话事实。
 *
 * <p>模型不能自行写入记忆，写入必须来自用户显式确认或管理端批准，并经过内容治理校验。</p>
 *
 * @author 余炘洋
 * @date 2026-10-18
 */
@Data
@Entity
@Table(name = "t_lingsql_engine_agent_memory_fact")
public class AgentMemoryFact {

    /**
     * 主键。
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 租户标识。
     */
    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId;

    /**
     * 用户标识，为空表示租户级记忆。
     */
    @Column(name = "user_id")
    private Long userId;

    /**
     * 记忆层级：PROCEDURAL 程序记忆 / PERMANENT 永久记忆。
     */
    @Column(name = "tier", nullable = false, length = 32)
    private String tier;

    /**
     * 记忆事实正文。
     */
    @Column(name = "fact_text", nullable = false, length = 500)
    private String factText;

    /**
     * 来源：USER_CONFIRMED 用户确认 / ADMIN_APPROVED 管理端批准。
     */
    @Column(name = "source_type", nullable = false, length = 32)
    private String sourceType;

    /**
     * 状态：ACTIVE 生效 / SUPERSEDED 已被新版本取代 / REVOKED 已撤销。
     */
    @Column(name = "status", nullable = false, length = 32)
    private String status;

    /**
     * 置信度分值 0-100。
     */
    @Column(name = "confidence", nullable = false)
    private Integer confidence;

    /**
     * 版本号，纠正生成更高版本。
     */
    @Column(name = "version", nullable = false)
    private Integer version;

    /**
     * 被当前版本取代的旧记忆主键。
     */
    @Column(name = "supersedes_id")
    private Long supersedesId;

    /**
     * 写入人标识。
     */
    @Column(name = "created_by", length = 64)
    private String createdBy;

    /**
     * 保留期到期时间，为空表示不自动过期。
     */
    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    /**
     * 创建时间。
     */
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /**
     * 更新时间。
     */
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
