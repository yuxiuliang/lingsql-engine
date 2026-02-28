package com.lingsql.employee.model;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 数字员工运营概览视图。
 *
 * @author 余炘洋
 * @date 2026-02-28
 */
@Data
public class DigitalEmployeeOverviewView {

    private String tenantId;
    private Integer windowHours;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Long totalEmployeeCount;
    private Long activeEmployeeCount;
    private Long runCount;
    private Long successCount;
    private Long failureCount;
    private Double successRate;
    private List<DigitalEmployeeTopEmployeeView> topEmployees;
}

