-- 为 UNKNOWN 物理调用保存唯一、不可变的人工资金裁决。

CREATE TABLE `t_lingsql_engine_billing_invocation_resolution`
(
    `id`                  bigint       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `resolution_id`       varchar(64)  NOT NULL COMMENT '裁决ID',
    `invocation_id`       varchar(64)  NOT NULL COMMENT '物理模型调用ID',
    `tenant_id`           varchar(64)  NOT NULL COMMENT '租户ID',
    `resolution_type`     varchar(32)  NOT NULL COMMENT 'CONFIRMED_USAGE/CONFIRMED_NOT_BILLED',
    `actual_tokens`       int          DEFAULT NULL COMMENT '确认的实际Token',
    `actual_amount`       bigint       NOT NULL COMMENT '确认的实际金额',
    `refund_amount`       bigint       NOT NULL COMMENT '退款金额',
    `provider_request_id` varchar(128) NOT NULL COMMENT '供应商请求ID',
    `evidence_hash`       char(64)     NOT NULL COMMENT '外部证据SHA-256摘要',
    `resolved_by`         varchar(64)  NOT NULL COMMENT '操作人',
    `resolved_at`         datetime     NOT NULL COMMENT '裁决时间',
    `create_time`         datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_billing_resolution_id` (`resolution_id`),
    UNIQUE KEY `uk_billing_resolution_invocation` (`invocation_id`),
    KEY `idx_billing_resolution_tenant_time` (`tenant_id`, `resolved_at`),
    KEY `idx_billing_resolution_type` (`resolution_type`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT ='物理模型调用资金裁决表';
