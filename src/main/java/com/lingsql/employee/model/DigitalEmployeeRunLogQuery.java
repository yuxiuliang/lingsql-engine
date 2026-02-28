package com.lingsql.employee.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 数字员工运行日志查询条件。
 *
 * @author 余炘洋
 * @date 2026-02-28
 */
@Data
public class DigitalEmployeeRunLogQuery {

    private String tenantId;
    private Long employeeId;
    private Integer success;
    private String triggerSource;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
}

