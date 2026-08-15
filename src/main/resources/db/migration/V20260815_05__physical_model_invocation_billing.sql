-- 为每次物理模型调用建立独立、可恢复的资金状态机。

CREATE TABLE `t_lingsql_engine_billing_invocation`
(
    `id`                  bigint         NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `reservation_id`      varchar(64)    NOT NULL COMMENT '资金预留ID',
    `invocation_id`       varchar(64)    NOT NULL COMMENT '物理模型调用幂等ID',
    `tenant_id`           varchar(64)    NOT NULL COMMENT '租户ID',
    `workflow_request_id` varchar(64)    NOT NULL COMMENT '工作流请求ID',
    `logical_call_no`     int            NOT NULL COMMENT '逻辑调用序号',
    `stage`               varchar(64)    NOT NULL COMMENT '调用阶段',
    `caller`              varchar(64)    NOT NULL COMMENT '调用来源',
    `route_attempt`       int            NOT NULL COMMENT '模型路由尝试序号',
    `provider_name`       varchar(64)    NOT NULL COMMENT '模型供应商',
    `model_name`          varchar(64)    NOT NULL COMMENT '模型名称',
    `prompt_hash`         char(64)       NOT NULL COMMENT 'Prompt SHA-256摘要',
    `max_billable_tokens` int            NOT NULL COMMENT '最大可计费Token',
    `actual_tokens`       int            DEFAULT NULL COMMENT '实际Token',
    `unit_cost`           bigint         NOT NULL COMMENT '冻结单价',
    `billing_weight`      decimal(18, 6) NOT NULL COMMENT '冻结计费权重',
    `reserved_amount`     bigint         NOT NULL COMMENT '预留金额',
    `actual_amount`       bigint         DEFAULT NULL COMMENT '实际金额',
    `refund_amount`       bigint         DEFAULT NULL COMMENT '退款金额',
    `billing_version`     varchar(64)    NOT NULL COMMENT '冻结计费版本',
    `provider_request_id` varchar(128)   DEFAULT NULL COMMENT '供应商请求ID',
    `state`               varchar(16)    NOT NULL COMMENT 'RESERVED/DISPATCHED/SETTLED/CANCELLED/UNKNOWN',
    `failure_code`        varchar(64)    DEFAULT NULL COMMENT '失败编码',
    `failure_message`     varchar(255)   DEFAULT NULL COMMENT '脱敏失败说明',
    `reserved_at`         datetime       NOT NULL COMMENT '预留时间',
    `dispatched_at`       datetime       DEFAULT NULL COMMENT '派发时间',
    `finalized_at`        datetime       DEFAULT NULL COMMENT '终态时间',
    `create_time`         datetime       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`         datetime       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `lock_version`        bigint         NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_billing_invocation_reservation` (`reservation_id`),
    UNIQUE KEY `uk_billing_invocation_id` (`invocation_id`),
    KEY `idx_billing_invocation_tenant_workflow` (`tenant_id`, `workflow_request_id`),
    KEY `idx_billing_invocation_state_reserved` (`state`, `reserved_at`),
    KEY `idx_billing_invocation_state_dispatched` (`state`, `dispatched_at`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT ='逐物理模型调用计费状态表';

ALTER TABLE `t_lingsql_engine_billing_ledger`
    ADD COLUMN `event_id` varchar(64) DEFAULT NULL COMMENT '不可变事件ID' AFTER `id`,
    ADD COLUMN `invocation_id` varchar(64) DEFAULT NULL COMMENT '物理模型调用ID' AFTER `event_id`,
    ADD COLUMN `workflow_request_id` varchar(64) DEFAULT NULL COMMENT '工作流请求ID' AFTER `invocation_id`,
    ADD COLUMN `event_name` varchar(16) DEFAULT NULL COMMENT 'RESERVE/REFUND/CLOSE/CANCEL' AFTER `workflow_request_id`,
    ADD UNIQUE KEY `uk_billing_ledger_event_id` (`event_id`),
    ADD KEY `idx_billing_ledger_invocation` (`invocation_id`),
    ADD KEY `idx_billing_ledger_workflow` (`tenant_id`, `workflow_request_id`);
