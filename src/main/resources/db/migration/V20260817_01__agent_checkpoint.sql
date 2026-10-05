-- Agent 持久检查点表
CREATE TABLE `t_lingsql_engine_agent_checkpoint` (
    `id`              bigint       NOT NULL AUTO_INCREMENT,
    `tenant_id`       varchar(64)  NOT NULL COMMENT '租户标识',
    `execution_id`    varchar(64)  NOT NULL COMMENT '执行标识',
    `conversation_id` varchar(64)  DEFAULT NULL COMMENT '会话标识',
    `stage`           varchar(64)  NOT NULL COMMENT '阶段',
    `status`          varchar(32)  NOT NULL COMMENT '状态',
    `payload_json`    json         DEFAULT NULL COMMENT '已脱敏载荷',
    `created_at`      datetime     NOT NULL COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_tenant_execution` (`tenant_id`, `execution_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent 持久检查点';
