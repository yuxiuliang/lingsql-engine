-- 目标：为已有环境补齐租户通知分组表，支持按租户维护通知通道分组。

CREATE TABLE IF NOT EXISTS `t_lingsql_engine_tenant_notification_group`
(
    `id`           bigint       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `tenant_id`    varchar(64)  NOT NULL COMMENT '租户ID',
    `group_code`   varchar(64)  NOT NULL COMMENT '分组编码',
    `group_name`   varchar(128) NOT NULL COMMENT '分组名称',
    `channels`     varchar(255) DEFAULT NULL COMMENT '通知通道列表，逗号分隔',
    `remark`       varchar(255) DEFAULT NULL COMMENT '备注',
    `is_deleted`   tinyint      NOT NULL DEFAULT '0' COMMENT '删除标记: 1-删除, 0-正常',
    `create_time`  datetime     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`  datetime     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tng_tenant_group` (`tenant_id`, `group_code`),
    KEY `idx_tng_tenant_deleted` (`tenant_id`, `is_deleted`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT ='租户通知分组表';
