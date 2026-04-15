package com.lingsql.employee.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 数字员工运行日志实体。
 *
 * @author 余炘洋
 * @date 2026-02-28
 */
@Data
@Entity
@Table(name = "t_lingsql_engine_digital_employee_run_log")
public class DigitalEmployeeRunLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "trigger_source")
    private String triggerSource;

    @Column(name = "question", columnDefinition = "TEXT")
    private String question;

    @Column(name = "answer", columnDefinition = "TEXT")
    private String answer;

    @Column(name = "executed_sql", columnDefinition = "TEXT")
    private String executedSql;

    @Column(name = "success")
    private Integer success;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "result_rows")
    private Integer resultRows;

    @Column(name = "request_id")
    private String requestId;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "alert_delivery_status")
    private Integer alertDeliveryStatus;

    @Column(name = "push_delivery_status")
    private Integer pushDeliveryStatus;

    @Column(name = "create_time")
    private LocalDateTime createTime;
}

