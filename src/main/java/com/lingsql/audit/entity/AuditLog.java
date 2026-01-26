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
     * 创建时间。
     *
     * @author 余炘洋
     * @date 2026-01-26
     */
    @Column(name = "create_time")
    private LocalDateTime createTime;
}
