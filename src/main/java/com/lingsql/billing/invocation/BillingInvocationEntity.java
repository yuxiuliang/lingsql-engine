package com.lingsql.billing.invocation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 逐物理模型调用资金记录。
 *
 * @author 余炘洋
 * @date 2026-08-15
 */
@Data
@Entity
@Table(name = "t_lingsql_engine_billing_invocation")
public class BillingInvocationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reservation_id", nullable = false, unique = true)
    private String reservationId;

    @Column(name = "invocation_id", nullable = false, unique = true)
    private String invocationId;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "workflow_request_id", nullable = false)
    private String workflowRequestId;

    @Column(name = "logical_call_no", nullable = false)
    private Integer logicalCallNo;

    @Column(name = "stage", nullable = false)
    private String stage;

    @Column(name = "caller", nullable = false)
    private String caller;

    @Column(name = "route_attempt", nullable = false)
    private Integer routeAttempt;

    @Column(name = "provider_name", nullable = false)
    private String providerName;

    @Column(name = "model_name", nullable = false)
    private String modelName;

    @Column(name = "prompt_hash", nullable = false)
    private String promptHash;

    @Column(name = "max_billable_tokens", nullable = false)
    private Integer maxBillableTokens;

    @Column(name = "actual_tokens")
    private Integer actualTokens;

    @Column(name = "unit_cost", nullable = false)
    private Long unitCost;

    @Column(name = "billing_weight", nullable = false, precision = 18, scale = 6)
    private BigDecimal billingWeight;

    @Column(name = "reserved_amount", nullable = false)
    private Long reservedAmount;

    @Column(name = "actual_amount")
    private Long actualAmount;

    @Column(name = "refund_amount")
    private Long refundAmount;

    @Column(name = "billing_version", nullable = false)
    private String billingVersion;

    @Column(name = "provider_request_id")
    private String providerRequestId;

    @Column(name = "state", nullable = false)
    private String state;

    @Column(name = "failure_code")
    private String failureCode;

    @Column(name = "failure_message")
    private String failureMessage;

    @Column(name = "reserved_at", nullable = false)
    private LocalDateTime reservedAt;

    @Column(name = "dispatched_at")
    private LocalDateTime dispatchedAt;

    @Column(name = "finalized_at")
    private LocalDateTime finalizedAt;

    @Column(name = "create_time", nullable = false)
    private LocalDateTime createTime;

    @Column(name = "update_time", nullable = false)
    private LocalDateTime updateTime;

    @Version
    @Column(name = "lock_version", nullable = false)
    private Long lockVersion;
}
