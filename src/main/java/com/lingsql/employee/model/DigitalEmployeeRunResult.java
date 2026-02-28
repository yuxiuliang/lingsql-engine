package com.lingsql.employee.model;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 数字员工执行结果。
 *
 * @author 余炘洋
 * @date 2026-02-28
 */
@Data
public class DigitalEmployeeRunResult {

    private Long runLogId;
    private Long employeeId;
    private String employeeName;
    private String tenantId;
    private String triggerSource;
    private String question;
    private String answer;
    private String sql;
    private List<Map<String, Object>> data;
    private Boolean success;
    private String errorMessage;
    private Long durationMs;
    private LocalDateTime executeTime;
}

