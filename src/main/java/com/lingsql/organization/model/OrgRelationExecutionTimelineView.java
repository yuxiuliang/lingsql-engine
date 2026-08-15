package com.lingsql.organization.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 组织关系执行轨迹视图。
 *
 * @author 余炘洋
 * @date 2026-04-12
 */
@Data
public class OrgRelationExecutionTimelineView {

    /**
     * 主键ID。
     */
    private Long id;

    /**
     * 执行项ID。
     */
    private Long executionId;

    /**
     * 事件类型。
     */
    private String eventType;

    /**
     * 事件摘要。
     */
    private String eventSummary;

    /**
     * 事件详情。
     */
    private String eventDetail;

    /**
     * 操作人ID。
     */
    private Long actorUserId;

    /**
     * 操作人名称。
     */
    private String actorName;

    /**
     * 变更前状态。
     */
    private String beforeStatus;

    /**
     * 变更后状态。
     */
    private String afterStatus;

    /**
     * 变更前处理人名称。
     */
    private String beforeAssigneeName;

    /**
     * 变更后处理人名称。
     */
    private String afterAssigneeName;

    /**
     * 变更前优先级。
     */
    private String beforePriority;

    /**
     * 变更后优先级。
     */
    private String afterPriority;

    /**
     * 变更前 SLA 时长。
     */
    private Integer beforeSlaHours;

    /**
     * 变更后 SLA 时长。
     */
    private Integer afterSlaHours;

    /**
     * 变更前截止时间。
     */
    private LocalDateTime beforeDueTime;

    /**
     * 变更后截止时间。
     */
    private LocalDateTime afterDueTime;

    /**
     * 变更前结果摘要。
     */
    private String beforeResultSummary;

    /**
     * 变更后结果摘要。
     */
    private String afterResultSummary;

    /**
     * 创建时间。
     */
    private LocalDateTime createTime;
}
