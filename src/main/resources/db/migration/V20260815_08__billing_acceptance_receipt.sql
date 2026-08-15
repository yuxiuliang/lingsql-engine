-- 冻结计费验收起止水位、MySQL 余额及精确请求归因，生成不可变验收回执。

CREATE TABLE `t_lingsql_engine_billing_acceptance_session`
(
    `id`               bigint      NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `session_id`       varchar(64) NOT NULL COMMENT '验收会话ID',
    `tenant_id`        varchar(64) NOT NULL COMMENT '租户ID',
    `balance_before`   bigint      NOT NULL COMMENT 'MySQL起始余额',
    `ledger_watermark` bigint      NOT NULL COMMENT '起始账本最大ID',
    `opened_by`        varchar(64) NOT NULL COMMENT '开启操作人',
    `opened_at`        datetime    NOT NULL COMMENT '开启时间',
    `create_time`      datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_billing_acceptance_session_id` (`session_id`),
    KEY `idx_billing_acceptance_session_tenant_time` (`tenant_id`, `opened_at`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT ='计费验收会话基线表';

CREATE TABLE `t_lingsql_engine_billing_acceptance_receipt`
(
    `id`                         bigint      NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `receipt_id`                 varchar(64) NOT NULL COMMENT '验收回执ID',
    `session_id`                 varchar(64) NOT NULL COMMENT '验收会话ID',
    `tenant_id`                  varchar(64) NOT NULL COMMENT '租户ID',
    `request_ids_json`           text        NOT NULL COMMENT '规范化精确请求ID集合',
    `request_ids_hash`           char(64)    NOT NULL COMMENT '请求集合SHA-256摘要',
    `request_count`              int         NOT NULL COMMENT '请求数量',
    `start_ledger_watermark`     bigint      NOT NULL COMMENT '起始账本水位',
    `end_ledger_watermark`       bigint      NOT NULL COMMENT '结束账本水位',
    `balance_before`             bigint      NOT NULL COMMENT 'MySQL起始余额',
    `balance_after`              bigint      NOT NULL COMMENT 'MySQL结束余额',
    `scope_net_amount`           bigint      NOT NULL COMMENT '精确请求集合净扣款',
    `interval_net_amount`        bigint      NOT NULL COMMENT '完整账本区间净扣款',
    `unrelated_event_count`      bigint      NOT NULL COMMENT '区间内非本批事件数量',
    `invocation_count`           bigint      NOT NULL COMMENT '物理调用总数',
    `settled_invocation_count`   bigint      NOT NULL COMMENT '已结算物理调用数',
    `cancelled_invocation_count` bigint      NOT NULL COMMENT '已取消物理调用数',
    `receipt_hash`               char(64)    NOT NULL COMMENT '回执内容SHA-256摘要',
    `closed_by`                  varchar(64) NOT NULL COMMENT '关闭操作人',
    `closed_at`                  datetime    NOT NULL COMMENT '关闭时间',
    `create_time`                datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_billing_acceptance_receipt_id` (`receipt_id`),
    UNIQUE KEY `uk_billing_acceptance_receipt_session` (`session_id`),
    UNIQUE KEY `uk_billing_acceptance_receipt_hash` (`receipt_hash`),
    KEY `idx_billing_acceptance_receipt_tenant_time` (`tenant_id`, `closed_at`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT ='计费验收不可变回执表';
