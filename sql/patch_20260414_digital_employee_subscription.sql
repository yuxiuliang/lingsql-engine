-- 目标：为数字员工补齐定时执行与订阅告警能力。

ALTER TABLE `t_lingsql_engine_digital_employee`
    ADD COLUMN IF NOT EXISTS `schedule_enabled` tinyint NOT NULL DEFAULT '0' COMMENT '是否启用定时执行: 1-启用, 0-停用' AFTER `default_question`;

ALTER TABLE `t_lingsql_engine_digital_employee`
    ADD COLUMN IF NOT EXISTS `schedule_cron` varchar(64) DEFAULT NULL COMMENT '定时执行 Cron' AFTER `schedule_enabled`;

ALTER TABLE `t_lingsql_engine_digital_employee`
    ADD COLUMN IF NOT EXISTS `alert_enabled` tinyint NOT NULL DEFAULT '0' COMMENT '是否启用告警: 1-启用, 0-停用' AFTER `schedule_cron`;

ALTER TABLE `t_lingsql_engine_digital_employee`
    ADD COLUMN IF NOT EXISTS `alert_rule_type` varchar(32) DEFAULT NULL COMMENT '告警规则类型' AFTER `alert_enabled`;

ALTER TABLE `t_lingsql_engine_digital_employee`
    ADD COLUMN IF NOT EXISTS `alert_threshold` int DEFAULT NULL COMMENT '结果行数阈值' AFTER `alert_rule_type`;

ALTER TABLE `t_lingsql_engine_digital_employee`
    ADD COLUMN IF NOT EXISTS `push_enabled` tinyint NOT NULL DEFAULT '0' COMMENT '是否启用结果推送: 1-启用, 0-停用' AFTER `alert_threshold`;

ALTER TABLE `t_lingsql_engine_digital_employee`
    ADD COLUMN IF NOT EXISTS `push_channels` varchar(128) DEFAULT NULL COMMENT '结果推送通道列表，逗号分隔' AFTER `push_enabled`;

ALTER TABLE `t_lingsql_engine_digital_employee`
    ADD COLUMN IF NOT EXISTS `push_max_rows` int DEFAULT NULL COMMENT '结果推送预览最大行数' AFTER `push_channels`;

ALTER TABLE `t_lingsql_engine_digital_employee`
    ADD COLUMN IF NOT EXISTS `push_title_template` varchar(255) DEFAULT NULL COMMENT '结果推送标题模板' AFTER `push_max_rows`;

ALTER TABLE `t_lingsql_engine_digital_employee`
    ADD COLUMN IF NOT EXISTS `push_message_template` text COMMENT '结果推送摘要模板' AFTER `push_title_template`;

ALTER TABLE `t_lingsql_engine_digital_employee`
    ADD COLUMN IF NOT EXISTS `last_schedule_time` datetime DEFAULT NULL COMMENT '最近一次计划触发时间' AFTER `last_run_time`;

ALTER TABLE `t_lingsql_engine_digital_employee_run_log`
    ADD COLUMN IF NOT EXISTS `alert_delivery_status` tinyint DEFAULT NULL COMMENT '告警投递状态: 1-成功, 0-失败, NULL-未触发' AFTER `duration_ms`;

ALTER TABLE `t_lingsql_engine_digital_employee_run_log`
    ADD COLUMN IF NOT EXISTS `push_delivery_status` tinyint DEFAULT NULL COMMENT '结果推送状态: 1-成功, 0-失败, NULL-未触发' AFTER `alert_delivery_status`;
