-- 计费资金事实统一到 MySQL，并补齐执行日志与账本关联字段。

ALTER TABLE `t_lingsql_engine_billing_ledger`
    DROP INDEX `uk_billing_ledger_request_seq_model`,
    ADD UNIQUE KEY `uk_billing_ledger_tenant_request_seq_model`
        (`tenant_id`, `request_id`, `event_seq`, `model_name`),
    ADD KEY `idx_billing_ledger_request_type` (`request_id`, `event_type`);

ALTER TABLE `t_lingsql_engine_sql_execute_log`
    ADD COLUMN `request_id` varchar(64) DEFAULT NULL COMMENT '请求ID' AFTER `id`,
    ADD KEY `idx_sql_request` (`request_id`);
