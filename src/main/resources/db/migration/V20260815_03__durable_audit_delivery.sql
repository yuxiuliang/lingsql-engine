-- 管理操作审计采用持久投递箱：操作前记录意图，操作后幂等发布最终日志。

ALTER TABLE `t_lingsql_engine_audit_log`
    ADD COLUMN `event_id` varchar(64) DEFAULT NULL COMMENT '审计事件唯一标识' AFTER `id`,
    ADD UNIQUE KEY `uk_audit_log_event_id` (`event_id`);

CREATE TABLE `t_lingsql_engine_audit_delivery`
(
    `id`                bigint       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `event_id`          varchar(64)  NOT NULL COMMENT '审计事件唯一标识',
    `tenant_id`         varchar(64)           DEFAULT NULL COMMENT '租户ID',
    `action`            varchar(64)           DEFAULT NULL COMMENT '操作动作',
    `resource`          varchar(128)          DEFAULT NULL COMMENT '操作对象',
    `payload_json`      longtext      NOT NULL COMMENT '脱敏后的不可变审计载荷',
    `outcome`           varchar(16)   NOT NULL COMMENT '结果: PREPARED/SUCCESS/FAILED/UNKNOWN',
    `delivery_status`   varchar(16)   NOT NULL COMMENT '投递状态: PENDING/DELIVERED/DEAD',
    `attempt_count`     int           NOT NULL DEFAULT 0 COMMENT '投递尝试次数',
    `next_attempt_time` datetime               DEFAULT NULL COMMENT '下次投递时间',
    `last_error`        text                   DEFAULT NULL COMMENT '最后失败分类',
    `finalize_time`     datetime               DEFAULT NULL COMMENT '业务结果确认时间',
    `create_time`       datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`       datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_audit_delivery_event_id` (`event_id`),
    KEY `idx_audit_delivery_status_next` (`delivery_status`, `next_attempt_time`),
    KEY `idx_audit_delivery_outcome_create` (`outcome`, `create_time`),
    KEY `idx_audit_delivery_tenant_create` (`tenant_id`, `create_time`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT ='审计持久投递箱';
