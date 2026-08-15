-- 目标：为数字员工运行日志补齐通道级投递诊断信息，便于运营排障与审计回看。

ALTER TABLE `t_lingsql_engine_digital_employee_run_log`
    ADD COLUMN IF NOT EXISTS `alert_delivery_detail` text COMMENT '告警投递诊断明细' AFTER `alert_delivery_status`;

ALTER TABLE `t_lingsql_engine_digital_employee_run_log`
    ADD COLUMN IF NOT EXISTS `push_delivery_detail` text COMMENT '结果推送诊断明细' AFTER `push_delivery_status`;
