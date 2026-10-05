-- 人工审批节点与租户插件授权
CREATE TABLE `t_lingsql_engine_agent_approval_node` (
    `id`            bigint      NOT NULL AUTO_INCREMENT,
    `tenant_id`     varchar(64) NOT NULL COMMENT '租户标识',
    `execution_id`  varchar(64) NOT NULL COMMENT '执行标识',
    `stage`         varchar(64) NOT NULL COMMENT '阶段',
    `status`        varchar(32) NOT NULL COMMENT '状态',
    `approver_id`   varchar(64) DEFAULT NULL COMMENT '审批人',
    `decided_at`    datetime    DEFAULT NULL COMMENT '决策时间',
    `comment`       varchar(512) DEFAULT NULL COMMENT '备注',
    `created_at`    datetime    NOT NULL COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_tenant_execution` (`tenant_id`, `execution_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent 人工审批节点';

CREATE TABLE `t_lingsql_engine_agent_plugin_grant` (
    `id`         bigint      NOT NULL AUTO_INCREMENT,
    `tenant_id`  varchar(64) NOT NULL COMMENT '租户标识',
    `plugin_id`  varchar(128) NOT NULL COMMENT '插件标识',
    `granted`    tinyint(1)  NOT NULL COMMENT '是否授权',
    `created_at` datetime    NOT NULL COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tenant_plugin` (`tenant_id`, `plugin_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='租户插件授权';
