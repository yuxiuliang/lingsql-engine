package com.lingsql.employee.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 数字员工运行日志视图。
 *
 * @author 余炘洋
 * @date 2026-02-28
 */
@Data
public class DigitalEmployeeRunLogView {

    private Long id;
    private Long employeeId;
    private String employeeName;
    private String tenantId;
    private String triggerSource;
    private String question;
    private String answer;
    private String executedSql;
    private Integer success;
    private String errorMessage;
    private Integer resultRows;
    private String requestId;
    private Long durationMs;
    private Integer alertDeliveryStatus;
    private Integer pushDeliveryStatus;
    private LocalDateTime createTime;
}

