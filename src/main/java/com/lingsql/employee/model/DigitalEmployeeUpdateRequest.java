package com.lingsql.employee.model;

import lombok.Data;

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
     * 状态: 1-启用, 0-停用。
     */
    private Integer status;

    /**
     * 备注。
     */
    private String remark;
}

