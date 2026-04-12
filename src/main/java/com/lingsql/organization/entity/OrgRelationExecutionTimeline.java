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
 * 组织关系执行轨迹实体。
 *
 * @author 余炘洋
 * @date 2026-04-12
 */
@Data
@Entity
@Table(name = "t_lingsql_engine_org_relation_execution_timeline")
public class OrgRelationExecutionTimeline {

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
     * 执行项ID。
     */
    @Column(name = "execution_id", nullable = false)
    private Long executionId;

    /**
     * 事件类型。
     */
    @Column(name = "event_type", nullable = false)
    private String eventType;

    /**
     * 事件摘要。
     */
    @Column(name = "event_summary", nullable = false)
    private String eventSummary;

    /**
     * 事件详情。
     */
    @Column(name = "event_detail", columnDefinition = "TEXT")
    private String eventDetail;

    /**
     * 操作人ID。
     */
    @Column(name = "actor_user_id")
    private Long actorUserId;

    /**
     * 操作人名称。
     */
    @Column(name = "actor_name")
    private String actorName;

    /**
     * 变更前状态。
     */
    @Column(name = "before_status")
    private String beforeStatus;

    /**
     * 变更后状态。
     */
    @Column(name = "after_status")
    private String afterStatus;

    /**
     * 变更前处理人名称。
     */
    @Column(name = "before_assignee_name")
    private String beforeAssigneeName;

    /**
     * 变更后处理人名称。
     */
    @Column(name = "after_assignee_name")
    private String afterAssigneeName;

    /**
     * 变更前优先级。
     */
    @Column(name = "before_priority")
    private String beforePriority;

    /**
     * 变更后优先级。
     */
    @Column(name = "after_priority")
    private String afterPriority;

    /**
     * 变更前 SLA 时长。
     */
    @Column(name = "before_sla_hours")
    private Integer beforeSlaHours;

    /**
     * 变更后 SLA 时长。
     */
    @Column(name = "after_sla_hours")
    private Integer afterSlaHours;

    /**
     * 变更前截止时间。
     */
    @Column(name = "before_due_time")
    private LocalDateTime beforeDueTime;

    /**
     * 变更后截止时间。
     */
    @Column(name = "after_due_time")
    private LocalDateTime afterDueTime;

    /**
     * 变更前结果摘要。
     */
    @Column(name = "before_result_summary", columnDefinition = "TEXT")
    private String beforeResultSummary;

    /**
     * 变更后结果摘要。
     */
    @Column(name = "after_result_summary", columnDefinition = "TEXT")
    private String afterResultSummary;

    /**
     * 创建时间。
     */
    @Column(name = "create_time")
    private LocalDateTime createTime;
}
