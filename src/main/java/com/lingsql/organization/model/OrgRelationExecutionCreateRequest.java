package com.lingsql.organization.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 组织关系执行项创建请求。
 *
 * @author 余炘洋
 * @date 2026-04-12
 */
@Data
public class OrgRelationExecutionCreateRequest {

    /**
     * 租户ID（系统管理员可指定）。
     */
    private String tenantId;

    /**
     * 源组织ID。
     */
    @NotNull(message = "源组织节点不能为空")
    private Long sourceOrgUnitId;

    /**
     * 关系类型。
     */
    @NotBlank(message = "关系类型不能为空")
    private String relationType;

    /**
     * 关系ID。
     * 当存在多个候选目标时，必须显式传入。
     */
    private Long relationId;

    /**
     * 执行标题。
     */
    @NotBlank(message = "执行标题不能为空")
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
    private String dueTime;

    /**
     * 指定处理人。
     */
    private Long assigneeUserId;
}
