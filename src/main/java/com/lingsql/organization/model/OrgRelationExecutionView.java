package com.lingsql.organization.model;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 组织关系执行台账视图。
 *
 * @author 余炘洋
 * @date 2026-04-12
 */
@Data
public class OrgRelationExecutionView {

    /**
     * 主键ID。
     */
    private Long id;

    /**
     * 租户ID。
     */
    private String tenantId;

    /**
     * 关系ID。
     */
    private Long relationId;

    /**
     * 关系类型。
     */
    private String relationType;

    /**
     * 关系名称。
     */
    private String relationName;

    /**
     * 场景编码。
     */
    private String sceneCode;

    /**
     * 场景名称。
     */
    private String sceneName;

    /**
     * 执行标题。
     */
    private String executionTitle;

    /**
     * 执行内容。
     */
    private String executionContent;

    /**
     * 优先级。
     */
    private String priority;

    /**
     * SLA 时长（小时）。
     */
    private Integer slaHours;

    /**
     * 截止时间。
     */
    private LocalDateTime dueTime;

    /**
     * SLA 状态。
     */
    private String slaStatus;

    /**
     * 是否已逾期。
     */
    private Boolean overdue;

    /**
     * 逾期分钟数。
     */
    private Long overdueMinutes;

    /**
     * 是否在 SLA 内完成。
     */
    private Boolean completedWithinSla;

    /**
     * 升级状态。
     */
    private String escalationStatus;

    /**
     * 升级级别。
     */
    private Integer escalationLevel;

    /**
     * 最近升级时间。
     */
    private LocalDateTime lastEscalatedTime;

    /**
     * 状态。
     */
    private String status;

    /**
     * 源组织ID。
     */
    private Long sourceOrgUnitId;

    /**
     * 源组织编码。
     */
    private String sourceOrgUnitCode;

    /**
     * 源组织名称。
     */
    private String sourceOrgUnitName;

    /**
     * 目标组织ID。
     */
    private Long targetOrgUnitId;

    /**
     * 目标组织编码。
     */
    private String targetOrgUnitCode;

    /**
     * 目标组织名称。
     */
    private String targetOrgUnitName;

    /**
     * 路由范围说明。
     */
    private String routeScopeSummary;

    /**
     * 路由范围节点数。
     */
    private Integer scopeUnitCount;

    /**
     * 路由范围编码快照。
     */
    private List<String> scopeUnitCodes = new ArrayList<>();

    /**
     * 创建人ID。
     */
    private Long requestedByUserId;

    /**
     * 创建人名称。
     */
    private String requestedByName;

    /**
     * 处理人ID。
     */
    private Long assigneeUserId;

    /**
     * 处理人名称。
     */
    private String assigneeName;

    /**
     * 处理结果。
     */
    private String resultSummary;

    /**
     * 开始处理时间。
     */
    private LocalDateTime startedTime;

    /**
     * 完成时间。
     */
    private LocalDateTime completedTime;

    /**
     * 创建时间。
     */
    private LocalDateTime createTime;

    /**
     * 更新时间。
     */
    private LocalDateTime updateTime;
}
