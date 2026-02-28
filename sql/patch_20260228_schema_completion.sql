-- Schema completion patch (2026-02-28)
-- 补齐商用链路缺失表，避免生产环境 ddl-auto=validate 启动失败。

CREATE TABLE IF NOT EXISTS `t_lingsql_engine_billing_plan`
(
    `id`                    bigint       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `plan_code`             varchar(64)  NOT NULL COMMENT '套餐编码',
    `plan_name`             varchar(128) NOT NULL COMMENT '套餐名称',
    `status`                tinyint      DEFAULT '1' COMMENT '状态: 1-启用, 0-停用',
    `service_level`         varchar(32)  DEFAULT NULL COMMENT '服务等级',
    `tenant_type`           varchar(32)  DEFAULT NULL COMMENT '租户类型',
    `default_quota_balance` bigint       NOT NULL DEFAULT 0 COMMENT '默认余额',
    `remark`                varchar(255) DEFAULT NULL COMMENT '备注',
    `is_deleted`            tinyint      NOT NULL DEFAULT '0' COMMENT '删除标记: 1-删除, 0-正常',
    `create_time`           datetime     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`           datetime     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_billing_plan_code` (`plan_code`),
    KEY `idx_billing_plan_status` (`status`),
    KEY `idx_billing_plan_deleted` (`is_deleted`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT ='套餐定义表';

CREATE TABLE IF NOT EXISTS `t_lingsql_engine_chat_history`
(
    `id`          bigint      NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `tenant_id`   varchar(64) NOT NULL COMMENT '租户ID',
    `user_id`     bigint      NOT NULL COMMENT '用户ID',
    `question`    text COMMENT '问题',
    `answer`      text COMMENT '回答',
    `sql_text`    text COMMENT 'SQL 文本',
    `data_json`   text COMMENT '结果数据(JSON)',
    `status`      tinyint     DEFAULT '1' COMMENT '状态: 1-正常, 0-异常',
    `create_time` datetime    DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_chat_history_tenant_user_time` (`tenant_id`, `user_id`, `create_time`),
    KEY `idx_chat_history_create` (`create_time`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT ='聊天历史记录表';

CREATE TABLE IF NOT EXISTS `t_lingsql_engine_metadata_ext`
(
    `id`                bigint       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `tenant_id`         varchar(64)  NOT NULL COMMENT '租户ID',
    `schema_name`       varchar(128) DEFAULT NULL COMMENT '数据库名称',
    `table_name`        varchar(128) NOT NULL COMMENT '表名',
    `column_name`       varchar(128) NOT NULL COMMENT '字段名',
    `business_comment`  text COMMENT '业务注释',
    `is_isolated_field` tinyint      DEFAULT '0' COMMENT '是否隔离字段: 1-是, 0-否',
    `create_time`       datetime     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`       datetime     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_metadata_ext_tenant_table_column` (`tenant_id`, `table_name`, `column_name`),
    KEY `idx_metadata_ext_tenant_table` (`tenant_id`, `table_name`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT ='元数据扩展字典表';

-- 清理历史脏数据，确保商用验收“套餐默认余额合法性”通过。
UPDATE `t_lingsql_engine_billing_plan`
SET `default_quota_balance` = 0
WHERE `default_quota_balance` IS NULL;

