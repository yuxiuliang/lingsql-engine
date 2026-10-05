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
 * 插件调用 Outbox 记录：统一落库可追踪、可补运、可计费。
 *
 * @author 余炘洋
 * @date 2026-10-05
 */
@Data
@Entity
@Table(name = "t_lingsql_engine_plugin_invocation_outbox")
public class PluginInvocationOutbox {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId;

    @Column(name = "execution_id", length = 64)
    private String executionId;

    @Column(name = "plugin_id", nullable = false, length = 128)
    private String pluginId;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "cost_units")
    private Integer costUnits;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
