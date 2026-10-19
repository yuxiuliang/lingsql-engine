ALTER TABLE `t_lingsql_engine_vendor_bill_event`
    ADD COLUMN `provider_request_id` varchar(128) DEFAULT NULL COMMENT '供应商请求标识' AFTER `external_bill_id`,
    ADD COLUMN `vendor_tokens` int DEFAULT NULL COMMENT '供应商账单 Token 数' AFTER `amount_minor`,
    ADD COLUMN `matched_invocation_id` varchar(64) DEFAULT NULL COMMENT '匹配的物理调用标识' AFTER `status`,
    ADD COLUMN `match_state` varchar(32) DEFAULT NULL COMMENT '匹配状态' AFTER `matched_invocation_id`,
    ADD COLUMN `match_time` datetime DEFAULT NULL COMMENT '匹配时间' AFTER `match_state`,
    ADD UNIQUE KEY `uk_vendor_bill_external` (`provider_name`, `external_bill_id`);
