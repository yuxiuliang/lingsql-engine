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
 * 审计投递箱记录。
 *
 * @author 余炘洋
 * @date 2026-08-15
 */
@Data
@Entity
@Table(name = "t_lingsql_engine_audit_delivery")
public class AuditDelivery {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, length = 64)
    private String eventId;

    @Column(name = "tenant_id", length = 64)
    private String tenantId;

    @Column(name = "action", length = 64)
    private String action;

    @Column(name = "resource", length = 128)
    private String resource;

    @Column(name = "payload_json", nullable = false, columnDefinition = "LONGTEXT")
    private String payloadJson;

    @Column(name = "outcome", nullable = false, length = 16)
    private String outcome;

    @Column(name = "delivery_status", nullable = false, length = 16)
    private String deliveryStatus;

    @Column(name = "attempt_count", nullable = false)
    private Integer attemptCount;

    @Column(name = "next_attempt_time")
    private LocalDateTime nextAttemptTime;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @Column(name = "finalize_time")
    private LocalDateTime finalizeTime;

    @Column(name = "create_time", nullable = false)
    private LocalDateTime createTime;

    @Column(name = "update_time", nullable = false)
    private LocalDateTime updateTime;
}
