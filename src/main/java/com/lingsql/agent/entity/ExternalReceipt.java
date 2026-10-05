package com.lingsql.agent.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 外部系统回执实体：记录只读对账性质的外部回执摘要，不用于执行业务写操作。
 *
 * @author 余炘洋
 * @date 2026-10-05
 */
@Data
@Entity
@Table(name = "t_lingsql_engine_external_receipt")
public class ExternalReceipt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId;

    @Column(name = "target_system", nullable = false, length = 128)
    private String targetSystem;

    @Column(name = "receipt_digest", nullable = false, length = 128)
    private String receiptDigest;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
