package com.lingsql.organization.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 组织关系执行台账实体。
 *
 * @author 余炘洋
 * @date 2026-04-12
 */
@Data
@Entity
@Table(name = "t_lingsql_engine_org_relation_execution")
public class OrgRelationExecution {

    /**
     * 主键ID。
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 租户ID。
     */
    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    /**
     * 关系ID。
     */
    @Column(name = "relation_id", nullable = false)
    private Long relationId;

    /**
     * 关系类型。
     */
    @Column(name = "relation_type", nullable = false)
    private String relationType;

    /**
     * 关系名称。
     */
    @Column(name = "relation_name")
    private String relationName;

    /**
     * 场景编码。
     */
    @Column(name = "scene_code")
    private String sceneCode;

    /**
     * 场景名称。
     */
    @Column(name = "scene_name")
    private String sceneName;

    /**
     * 执行标题。
     */
    @Column(name = "execution_title", nullable = false)
    private String executionTitle;

    /**
     * 执行内容。
     */
    @Column(name = "execution_content", columnDefinition = "TEXT")
    private String executionContent;

    /**
     * 优先级。
     */
    @Column(name = "priority", nullable = false)
    private String priority;

    /**
     * SLA 时长（小时）。
     */
    @Column(name = "sla_hours")
    private Integer slaHours;

    /**
     * 截止时间。
     */
    @Column(name = "due_time")
    private LocalDateTime dueTime;

    /**
     * 升级状态。
     */
    @Column(name = "escalation_status")
    private String escalationStatus;

    /**
     * 升级级别。
     */
    @Column(name = "escalation_level")
    private Integer escalationLevel;

    /**
     * 最近升级时间。
     */
    @Column(name = "last_escalated_time")
    private LocalDateTime lastEscalatedTime;

    /**
     * 状态。
     */
    @Column(name = "status", nullable = false)
    private String status;

    /**
     * 源组织ID。
     */
    @Column(name = "source_org_unit_id", nullable = false)
    private Long sourceOrgUnitId;

    /**
     * 源组织编码。
     */
    @Column(name = "source_org_unit_code")
    private String sourceOrgUnitCode;

    /**
     * 源组织名称。
     */
    @Column(name = "source_org_unit_name")
    private String sourceOrgUnitName;

    /**
     * 目标组织ID。
     */
    @Column(name = "target_org_unit_id", nullable = false)
    private Long targetOrgUnitId;

    /**
     * 目标组织编码。
     */
    @Column(name = "target_org_unit_code")
    private String targetOrgUnitCode;

    /**
     * 目标组织名称。
     */
    @Column(name = "target_org_unit_name")
    private String targetOrgUnitName;

    /**
     * 路由范围说明。
     */
    @Column(name = "route_scope_summary")
    private String routeScopeSummary;

    /**
     * 路由范围节点数。
     */
    @Column(name = "scope_unit_count")
    private Integer scopeUnitCount;

    /**
     * 路由范围编码快照。
     */
    @Column(name = "scope_unit_codes_json", columnDefinition = "TEXT")
    private String scopeUnitCodesJson;

    /**
     * 创建人ID。
     */
    @Column(name = "requested_by_user_id", nullable = false)
    private Long requestedByUserId;

    /**
     * 创建人名称。
     */
    @Column(name = "requested_by_name")
    private String requestedByName;

    /**
     * 处理人ID。
     */
    @Column(name = "assignee_user_id")
    private Long assigneeUserId;

    /**
     * 处理人名称。
     */
    @Column(name = "assignee_name")
    private String assigneeName;

    /**
     * 处理结果。
     */
    @Column(name = "result_summary", columnDefinition = "TEXT")
    private String resultSummary;

    /**
     * 开始处理时间。
     */
    @Column(name = "started_time")
    private LocalDateTime startedTime;

    /**
     * 完成时间。
     */
    @Column(name = "completed_time")
    private LocalDateTime completedTime;

    /**
     * 创建时间。
     */
    @Column(name = "create_time")
    private LocalDateTime createTime;

    /**
     * 更新时间。
     */
    @Column(name = "update_time")
    private LocalDateTime updateTime;
}
