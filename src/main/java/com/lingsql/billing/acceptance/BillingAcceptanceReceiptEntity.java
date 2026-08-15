package com.lingsql.billing.acceptance;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.Data;
import org.hibernate.annotations.Immutable;

import java.time.LocalDateTime;

/**
 * 不可变计费验收完成证据。
 *
 * @author 余炘洋
 * @date 2026-08-15
 */
@Data
@Entity
@Immutable
@Table(name = "t_lingsql_engine_billing_acceptance_receipt")
class BillingAcceptanceReceiptEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "receipt_id", nullable = false, unique = true)
    private String receiptId;

    @Column(name = "session_id", nullable = false, unique = true)
    private String sessionId;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Lob
    @Column(name = "request_ids_json", nullable = false, columnDefinition = "text")
    private String requestIdsJson;

    @Column(name = "request_ids_hash", nullable = false)
    private String requestIdsHash;

    @Column(name = "request_count", nullable = false)
    private Integer requestCount;

    @Column(name = "start_ledger_watermark", nullable = false)
    private Long startLedgerWatermark;

    @Column(name = "end_ledger_watermark", nullable = false)
    private Long endLedgerWatermark;

    @Column(name = "balance_before", nullable = false)
    private Long balanceBefore;

    @Column(name = "balance_after", nullable = false)
    private Long balanceAfter;

    @Column(name = "scope_net_amount", nullable = false)
    private Long scopeNetAmount;

    @Column(name = "interval_net_amount", nullable = false)
    private Long intervalNetAmount;

    @Column(name = "unrelated_event_count", nullable = false)
    private Long unrelatedEventCount;

    @Column(name = "invocation_count", nullable = false)
    private Long invocationCount;

    @Column(name = "settled_invocation_count", nullable = false)
    private Long settledInvocationCount;

    @Column(name = "cancelled_invocation_count", nullable = false)
    private Long cancelledInvocationCount;

    @Column(name = "receipt_hash", nullable = false, unique = true)
    private String receiptHash;

    @Column(name = "closed_by", nullable = false)
    private String closedBy;

    @Column(name = "closed_at", nullable = false)
    private LocalDateTime closedAt;

    @Column(name = "create_time", nullable = false)
    private LocalDateTime createTime;
}
