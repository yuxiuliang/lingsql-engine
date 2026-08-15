package com.lingsql.billing.invocation;

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
 * 物理调用资金裁决不可变记录。
 *
 * @author 余炘洋
 * @date 2026-08-15
 */
@Data
@Entity
@Immutable
@Table(name = "t_lingsql_engine_billing_invocation_resolution")
public class BillingInvocationResolutionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "resolution_id", nullable = false, unique = true)
    private String resolutionId;

    @Column(name = "invocation_id", nullable = false, unique = true)
    private String invocationId;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "resolution_type", nullable = false)
    private String resolutionType;

    @Column(name = "actual_tokens")
    private Integer actualTokens;

    @Column(name = "actual_amount", nullable = false)
    private Long actualAmount;

    @Column(name = "refund_amount", nullable = false)
    private Long refundAmount;

    @Column(name = "provider_request_id", nullable = false)
    private String providerRequestId;

    @Column(name = "evidence_hash", nullable = false)
    private String evidenceHash;

    @Column(name = "resolved_by", nullable = false)
    private String operator;

    @Column(name = "resolved_at", nullable = false)
    private LocalDateTime resolvedAt;

    @Column(name = "create_time", nullable = false)
    private LocalDateTime createTime;
}
