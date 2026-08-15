package com.lingsql.audit.model;

import lombok.Data;

/**
 * 审计日志写入请求。
 *
 * @author 余炘洋
 * @date 2026-01-26
 */
@Data
public class AuditLogCommand {

    /**
     * 审计事件唯一标识，用于补偿重放幂等。
     */
    private String eventId;

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
     * 真实操作人ID。
     *
     * @author 余炘洋
     * @date 2026-01-27
     */
    private Long realUserId;

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
     * 操作详情(JSON)。
     *
     * @author 余炘洋
     * @date 2026-01-26
     */
    private String detail;

    /**
     * 人类可读摘要。
     *
     * @author 余炘洋
     * @date 2026-01-27
     */
    private String summary;

    /**
     * 是否成功。
     *
     * @author 余炘洋
     * @date 2026-01-26
     */
    private boolean success = true;

    /**
     * 错误信息。
     *
     * @author 余炘洋
     * @date 2026-01-26
     */
    private String errorMessage;

    /**
     * 请求来源IP。
     *
     * @author 余炘洋
     * @date 2026-01-26
     */
    private String requestIp;

    /**
     * 浏览器 User-Agent。
     *
     * @author 余炘洋
     * @date 2026-01-27
     */
    private String userAgent;
}
