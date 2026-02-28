package com.lingsql.employee.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 数字员工视图。
 *
 * @author 余炘洋
 * @date 2026-02-28
 */
@Data
public class DigitalEmployeeView {

    private Long id;
    private String tenantId;
    private String employeeCode;
    private String employeeName;
    private String roleName;
    private String scenario;
    private String promptTemplate;
    private String defaultQuestion;
    private Integer status;
    private Long runCount;
    private Long successCount;
    private Integer lastRunStatus;
    private LocalDateTime lastRunTime;
    private String lastErrorMessage;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}

