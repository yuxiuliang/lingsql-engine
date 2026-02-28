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
 * 数字员工定义实体。
 *
 * @author 余炘洋
 * @date 2026-02-28
 */
@Data
@Entity
@Table(name = "t_lingsql_engine_digital_employee")
public class DigitalEmployee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "employee_code", nullable = false)
    private String employeeCode;

    @Column(name = "employee_name", nullable = false)
    private String employeeName;

    @Column(name = "role_name")
    private String roleName;

    @Column(name = "scenario")
    private String scenario;

    @Column(name = "prompt_template", columnDefinition = "TEXT")
    private String promptTemplate;

    @Column(name = "default_question", columnDefinition = "TEXT")
    private String defaultQuestion;

    @Column(name = "status")
    private Integer status;

    @Column(name = "run_count")
    private Long runCount;

    @Column(name = "success_count")
    private Long successCount;

    @Column(name = "last_run_status")
    private Integer lastRunStatus;

    @Column(name = "last_run_time")
    private LocalDateTime lastRunTime;

    @Column(name = "last_error_message", columnDefinition = "TEXT")
    private String lastErrorMessage;

    @Column(name = "remark")
    private String remark;

    @Column(name = "is_deleted")
    private Integer isDeleted;

    @Column(name = "create_time")
    private LocalDateTime createTime;

    @Column(name = "update_time")
    private LocalDateTime updateTime;
}

