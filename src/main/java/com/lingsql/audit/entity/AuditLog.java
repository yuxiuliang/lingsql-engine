package com.lingsql.audit.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理操作审计日志实体。
 *
 * @author 余炘洋
 * @date 2026-01-26
 */
@Data
@Entity
@Table(name = "t_lingsql_engine_audit_log")
public class AuditLog {

    /**
     * 审计事件唯一标识。
     */
    @Column(name = "event_id", length = 64)
    private String eventId;

    /**
     * 主键ID。
     *
     * @author 余炘洋
     * @date 2026-01-26
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 租户ID。
     *
     * @author 余炘洋
     * @date 2026-01-26
     */
    @Column(name = "tenant_id")
    private String tenantId;

    /**
     * 操作人ID。
     *
     * @author 余炘洋
     * @date 2026-01-26
     */
    @Column(name = "user_id")
    private Long userId;

    /**
     * 真实操作人ID。
     *
     * @author 余炘洋
     * @date 2026-01-27
     */
    @Column(name = "real_user_id")
    private Long realUserId;

    /**
     * 操作人账号。
     *
     * @author 余炘洋
     * @date 2026-01-26
     */
    @Column(name = "username")
    private String username;

    /**
     * 操作动作。
     *
     * @author 余炘洋
     * @date 2026-01-26
     */
    @Column(name = "action")
    private String action;

    /**
     * 操作对象。
     *
     * @author 余炘洋
     * @date 2026-01-26
     */
    @Column(name = "resource")
    private String resource;

    /**
     * 操作详情(JSON)。
     *
     * @author 余炘洋
     * @date 2026-01-26
     */
    @Column(name = "detail", columnDefinition = "TEXT")
    private String detail;

    /**
     * 人类可读摘要。
     *
     * @author 余炘洋
     * @date 2026-01-27
     */
    @Column(name = "summary", length = 255)
    private String summary;

    /**
     * 是否成功。
     *
     * @author 余炘洋
     * @date 2026-01-26
     */
    @Column(name = "success")
    private Integer success;

    /**
     * 错误信息。
     *
     * @author 余炘洋
     * @date 2026-01-26
     */
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    /**
     * 请求来源IP。
     *
     * @author 余炘洋
     * @date 2026-01-26
     */
    @Column(name = "request_ip")
    private String requestIp;

    /**
     * 浏览器 User-Agent。
     *
     * @author 余炘洋
     * @date 2026-01-27
     */
    @Column(name = "user_agent", length = 512)
    private String userAgent;

    /**
     * 创建时间。
     *
     * @author 余炘洋
     * @date 2026-01-26
     */
    @Column(name = "create_time")
    private LocalDateTime createTime;
}
