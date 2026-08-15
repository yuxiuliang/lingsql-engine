package com.lingsql.billing.acceptance;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import org.hibernate.annotations.Immutable;

import java.time.LocalDateTime;

/**
 * 不可变计费验收会话基线。
 *
 * @author 余炘洋
 * @date 2026-08-15
 */
@Data
@Entity
@Immutable
@Table(name = "t_lingsql_engine_billing_acceptance_session")
class BillingAcceptanceSessionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false, unique = true)
    private String sessionId;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "balance_before", nullable = false)
    private Long balanceBefore;

    @Column(name = "ledger_watermark", nullable = false)
    private Long ledgerWatermark;

    @Column(name = "opened_by", nullable = false)
    private String openedBy;

    @Column(name = "opened_at", nullable = false)
    private LocalDateTime openedAt;

    @Column(name = "create_time", nullable = false)
    private LocalDateTime createTime;
}
