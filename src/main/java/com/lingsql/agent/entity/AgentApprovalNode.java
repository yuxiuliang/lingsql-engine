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
 * 人工审批节点记录：用于等待/续跑的安全恢复点，不绕过 SQL 审计模块。
 *
 * @author 余炘洋
 * @date 2026-10-05
 */
@Data
@Entity
@Table(name = "t_lingsql_engine_agent_approval_node")
public class AgentApprovalNode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId;

    @Column(name = "execution_id", nullable = false, length = 64)
    private String executionId;

    @Column(name = "stage", nullable = false, length = 64)
    private String stage;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "approver_id", length = 64)
    private String approverId;

    @Column(name = "decided_at")
    private LocalDateTime decidedAt;

    @Column(name = "comment", length = 512)
    private String comment;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
