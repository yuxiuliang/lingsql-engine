package com.lingsql.employee.model;

import lombok.Data;

import java.util.List;

/**
 * 数字员工更新请求。
 *
 * @author 余炘洋
 * @date 2026-02-28
 */
@Data
public class DigitalEmployeeUpdateRequest {

    /**
     * 目标租户ID。
     */
    private String tenantId;

    /**
     * 员工编码。
     */
    private String employeeCode;

    /**
     * 员工名称。
     */
    private String employeeName;

    /**
     * 岗位角色。
     */
    private String roleName;

    /**
     * 业务场景。
     */
    private String scenario;

    /**
     * 提示词模板。
     */
    private String promptTemplate;

    /**
     * 默认执行问题。
     */
    private String defaultQuestion;

    /**
     * 是否启用定时执行: 1-启用, 0-停用。
     */
    private Integer scheduleEnabled;

    /**
     * 定时执行 Cron。
     */
    private String scheduleCron;

    /**
     * 是否启用告警: 1-启用, 0-停用。
     */
    private Integer alertEnabled;

    /**
     * 告警规则类型。
     */
    private String alertRuleType;

    /**
     * 结果行数阈值。
     */
    private Integer alertThreshold;

    /**
     * 是否启用结果推送: 1-启用, 0-停用。
     */
    private Integer pushEnabled;

    /**
     * 结果推送通道列表。
     */
    private List<String> pushChannels;

    /**
     * 推送时附带的结果预览最大行数。
     */
    private Integer pushMaxRows;

    /**
     * 推送标题模板。
     */
    private String pushTitleTemplate;

    /**
     * 推送摘要模板。
     */
    private String pushMessageTemplate;

    /**
     * 状态: 1-启用, 0-停用。
     */
    private Integer status;

    /**
     * 备注。
     */
    private String remark;
}

