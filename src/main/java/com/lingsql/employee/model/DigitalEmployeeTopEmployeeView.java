package com.lingsql.employee.model;

import lombok.Data;

/**
 * 数字员工运营排行视图。
 *
 * @author 余炘洋
 * @date 2026-02-28
 */
@Data
public class DigitalEmployeeTopEmployeeView {

    private Long employeeId;
    private String employeeName;
    private Long runCount;
    private Long successCount;
    private Long failureCount;
    private Double successRate;
}

