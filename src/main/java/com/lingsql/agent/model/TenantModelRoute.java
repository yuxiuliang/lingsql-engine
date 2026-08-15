package com.lingsql.agent.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 租户模型授权路由。物理模型仍由系统统一管理，本实体只表达租户可使用的模型及路由参数。
 *
 * @author 余炘洋
 * @date 2026-08-16
 */
@Data
@Entity
@Table(name = "t_lingsql_engine_tenant_model_route")
public class TenantModelRoute {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId;

    @Column(name = "model_config_id", nullable = false)
    private Long modelConfigId;

    @Column(name = "route_priority", nullable = false)
    private Integer routePriority;

    @Column(name = "route_weight", nullable = false)
    private Integer routeWeight;

    @Column(name = "status", nullable = false)
    private Integer status;

    @Column(name = "create_time", insertable = false, updatable = false)
    private LocalDateTime createTime;

    @Column(name = "update_time", insertable = false, updatable = false)
    private LocalDateTime updateTime;
}
