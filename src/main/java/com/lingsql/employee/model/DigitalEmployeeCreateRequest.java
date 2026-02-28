package com.lingsql.employee.model;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 数字员工创建请求。
 *
 * @author 余炘洋
 * @date 2026-02-28
 */
@Data
public class DigitalEmployeeCreateRequest {

    /**
     * 目标租户ID（执行 SQL 的租户）。
     */
    @NotBlank(message = "租户ID不能为空")
    private String tenantId;

    /**
     * 员工编码（可为空，系统自动生成）。
     */
    private String employeeCode;

    /**
     * 员工名称。
     */
    @NotBlank(message = "员工名称不能为空")
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
    @NotBlank(message = "默认执行问题不能为空")
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

