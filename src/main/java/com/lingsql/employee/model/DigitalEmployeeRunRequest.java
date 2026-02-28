package com.lingsql.employee.model;

import lombok.Data;

/**
 * 数字员工执行请求。
 *
 * @author 余炘洋
 * @date 2026-02-28
 */
@Data
public class DigitalEmployeeRunRequest {

    /**
     * 本次执行问题（可覆盖默认问题）。
     */
    private String question;

    /**
     * 触发来源（默认 MANUAL）。
     */
    private String triggerSource;
}

