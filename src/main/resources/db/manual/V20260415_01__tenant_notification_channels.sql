ALTER TABLE `t_lingsql_engine_tenant`
    ADD COLUMN IF NOT EXISTS `notification_channels` varchar(128) DEFAULT NULL COMMENT '默认通知通道列表，逗号分隔' AFTER `contact_email`;

ALTER TABLE `t_lingsql_engine_digital_employee`
    ADD COLUMN IF NOT EXISTS `push_channels` varchar(128) DEFAULT NULL COMMENT '结果推送通道列表，逗号分隔' AFTER `push_enabled`;
