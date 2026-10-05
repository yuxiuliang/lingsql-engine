CREATE TABLE `t_lingsql_engine_external_receipt` (
    `id`            bigint      NOT NULL AUTO_INCREMENT,
    `tenant_id`     varchar(64) NOT NULL COMMENT '租户标识',
    `target_system` varchar(128) NOT NULL COMMENT '目标系统',
    `receipt_digest` varchar(128) NOT NULL COMMENT '回执摘要',
    `status`        varchar(32) NOT NULL COMMENT '状态',
    `created_at`    datetime    NOT NULL COMMENT '创建时间',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='外部系统回执';

CREATE TABLE `t_lingsql_engine_vendor_bill_event` (
    `id`            bigint      NOT NULL AUTO_INCREMENT,
    `tenant_id`     varchar(64) DEFAULT NULL COMMENT '租户标识',
    `provider_name` varchar(128) DEFAULT NULL COMMENT '供应商',
    `external_bill_id` varchar(128) DEFAULT NULL COMMENT '外部账单标识',
    `amount_minor`  bigint      DEFAULT NULL COMMENT '金额（分）',
    `currency`      varchar(16) DEFAULT NULL COMMENT '币种',
    `status`        varchar(32) DEFAULT NULL COMMENT '状态',
    `created_at`    datetime    NOT NULL COMMENT '创建时间',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='供应商账单事件';
