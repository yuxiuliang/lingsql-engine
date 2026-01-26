package com.lingsql.audit.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 审计日志视图。
 *
 * @author 余炘洋
 * @date 2026-01-26
 */
@Data
public class AuditLogView {

    /**
     * ID。
     *
     * @author 余炘洋
     * @date 2026-01-26
     */
    private Long id;

    /**
     * 租户ID。
     *
     * @author 余炘洋
     * @date 2026-01-26
     */
    private String tenantId;

    /**
     * 用户ID。
     *
     * @author 余炘洋
     * @date 2026-01-26
     */
    private Long userId;

    /**
     * 用户账号。
     *
     * @author 余炘洋
     * @date 2026-01-26
     */
    private String username;

    /**
     * 操作动作。
     *
     * @author 余炘洋
     * @date 2026-01-26
     */
    private String action;

    /**
     * 操作对象。
     *
     * @author 余炘洋
     * @date 2026-01-26
     */
    private String resource;

    /**
     * 操作详情。
     *
     * @author 余炘洋
     * @date 2026-01-26
     */
    private String detail;

    /**
     * 是否成功。
     *
     * @author 余炘洋
     * @date 2026-01-26
     */
    private Integer success;

    /**
     * 错误信息。
     *
     * @author 余炘洋
     * @date 2026-01-26
     */
    private String errorMessage;

    /**
     * 请求IP。
     *
     * @author 余炘洋
     * @date 2026-01-26
     */
    private String requestIp;

    /**
     * 创建时间。
     *
     * @author 余炘洋
     * @date 2026-01-26
     */
    private LocalDateTime createTime;
}
