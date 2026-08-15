package com.lingsql.organization.model;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 组织关系执行项更新请求。
 *
 * @author 余炘洋
 * @date 2026-04-12
 */
@Data
public class OrgRelationExecutionUpdateRequest {

    /**
     * 状态。
     */
    @NotBlank(message = "执行状态不能为空")
    private String status;

    /**
     * 处理人ID。
     */
    private Long assigneeUserId;

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
     * 处理结果。
     */
    private String resultSummary;
}
