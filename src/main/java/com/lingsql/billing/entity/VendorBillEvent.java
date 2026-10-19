package com.lingsql.billing.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 供应商账单事件实体。
 *
 * @author 余炘洋
 * @date 2026-10-05
 */
@Data
@Entity
@Table(name = "t_lingsql_engine_vendor_bill_event")
public class VendorBillEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", length = 64)
    private String tenantId;

    @Column(name = "provider_name", length = 128)
    private String providerName;

    @Column(name = "external_bill_id", length = 128)
    private String externalBillId;

    /**
     * 供应商请求标识，用于匹配平台物理调用。
     *
     * @author 余炘洋
     * @date 2026-10-17
     */
    @Column(name = "provider_request_id", length = 128)
    private String providerRequestId;

    @Column(name = "amount_minor")
    private Long amountMinor;

    /**
     * 供应商账单 Token 数，缺省表示账单未提供。
     *
     * @author 余炘洋
     * @date 2026-10-17
     */
    @Column(name = "vendor_tokens")
    private Integer vendorTokens;

    @Column(name = "currency", length = 16)
    private String currency;

    @Column(name = "status", length = 32)
    private String status;

    /**
     * 匹配到的物理调用标识。
     *
     * @author 余炘洋
     * @date 2026-10-17
     */
    @Column(name = "matched_invocation_id", length = 64)
    private String matchedInvocationId;

    /**
     * 匹配状态，取值见 VendorBillMatchState。
     *
     * @author 余炘洋
     * @date 2026-10-17
     */
    @Column(name = "match_state", length = 32)
    private String matchState;

    /**
     * 匹配时间。
     *
     * @author 余炘洋
     * @date 2026-10-17
     */
    @Column(name = "match_time")
    private LocalDateTime matchTime;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
