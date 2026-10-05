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
 * 租户插件授权实体：控制租户可使用的插件白名单，防止默认暴露全部插件。
 *
 * @author 余炘洋
 * @date 2026-10-05
 */
@Data
@Entity
@Table(name = "t_lingsql_engine_agent_plugin_grant")
public class AgentPluginGrant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId;

    @Column(name = "plugin_id", nullable = false, length = 128)
    private String pluginId;

    @Column(name = "granted", nullable = false)
    private Boolean granted;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
