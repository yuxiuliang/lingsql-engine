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
 * Agent 持久检查点实体：记录编排阶段快照，用于有界恢复与审计。
 *
 * @author 余炘洋
 * @date 2026-10-05
 */
@Data
@Entity
@Table(name = "t_lingsql_engine_agent_checkpoint")
public class AgentCheckpoint {

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
     * 执行标识。
     */
    @Column(name = "execution_id", nullable = false, length = 64)
    private String executionId;

    /**
     * 会话标识。
     */
    @Column(name = "conversation_id", length = 64)
    private String conversationId;

    /**
     * 阶段。
     */
    @Column(name = "stage", nullable = false, length = 64)
    private String stage;

    /**
     * 状态。
     */
    @Column(name = "status", nullable = false, length = 32)
    private String status;

    /**
     * 载荷（JSON，已脱敏）。
     */
    @Column(name = "payload_json", columnDefinition = "json")
    private String payloadJson;

    /**
     * 创建时间。
     */
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
