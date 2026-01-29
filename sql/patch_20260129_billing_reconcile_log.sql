-- Billing reconcile log schema patch (2026-01-29)
-- Adds reconciliation workflow columns and aligns CLOSE semantics.

ALTER TABLE `t_lingsql_engine_billing_reconcile_log`
    ADD COLUMN `status` tinyint DEFAULT '0' COMMENT '状态: 0-未处理, 1-已处理' AFTER `diff_amount`,
    ADD COLUMN `resolve_note` varchar(255) DEFAULT NULL COMMENT '处理备注' AFTER `status`,
    ADD COLUMN `resolve_user_id` bigint DEFAULT NULL COMMENT '处理人ID' AFTER `resolve_note`,
    ADD COLUMN `resolve_time` datetime DEFAULT NULL COMMENT '处理时间' AFTER `resolve_user_id`,
    ADD COLUMN `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间' AFTER `create_time`,
    MODIFY COLUMN `close_total` bigint DEFAULT NULL COMMENT 'CLOSE 总额';

ALTER TABLE `t_lingsql_engine_billing_ledger`
    MODIFY COLUMN `event_type` tinyint DEFAULT '0' COMMENT '事件类型: 0-预扣, 1-退款, 2-CLOSE(对账)';
