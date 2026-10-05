CREATE TABLE `t_lingsql_engine_plugin_invocation_outbox` (
    `id`           bigint      NOT NULL AUTO_INCREMENT,
    `tenant_id`    varchar(64) NOT NULL COMMENT '租户标识',
    `execution_id` varchar(64) DEFAULT NULL COMMENT '执行标识',
    `plugin_id`    varchar(128) NOT NULL COMMENT '插件标识',
    `status`       varchar(32) NOT NULL COMMENT '状态',
    `cost_units`   int DEFAULT NULL COMMENT '成本单位',
    `created_at`   datetime    NOT NULL COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_tenant_execution` (`tenant_id`, `execution_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='插件调用 Outbox';
