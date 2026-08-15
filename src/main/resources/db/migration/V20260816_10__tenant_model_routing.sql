-- 为租户显式授权可使用的物理模型，未授权租户不得回退到全局模型池。

CREATE TABLE `t_lingsql_engine_tenant_model_route`
(
    `id`               bigint      NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `tenant_id`        varchar(64) NOT NULL COMMENT '租户ID',
    `model_config_id`  bigint      NOT NULL COMMENT '物理模型配置ID',
    `route_priority`   int         NOT NULL COMMENT '租户内路由优先级，数值越小越优先',
    `route_weight`     int         NOT NULL COMMENT '同优先级模型的租户内权重',
    `status`           int         NOT NULL DEFAULT 1 COMMENT '状态：1-启用，0-禁用',
    `create_time`      datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`      datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tenant_model_route` (`tenant_id`, `model_config_id`),
    KEY `idx_tenant_model_route_model` (`model_config_id`),
    KEY `idx_tenant_model_route_select` (`tenant_id`, `status`, `route_priority`, `id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT ='租户模型授权路由';
