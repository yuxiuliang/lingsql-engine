package com.lingsql.employee.model;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

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
    private Integer scheduleEnabled;
    private String scheduleCron;
    private Integer alertEnabled;
    private String alertRuleType;
    private Integer alertThreshold;
    private Integer pushEnabled;
    private List<String> pushChannels;
    private Integer pushMaxRows;
    private String pushTitleTemplate;
    private String pushMessageTemplate;
    private Integer status;
    private Long runCount;
    private Long successCount;
    private Integer lastRunStatus;
    private LocalDateTime lastRunTime;
    private LocalDateTime lastScheduleTime;
    private String lastErrorMessage;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}

