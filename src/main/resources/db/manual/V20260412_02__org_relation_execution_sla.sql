ALTER TABLE `t_lingsql_engine_org_relation_execution`
  ADD COLUMN IF NOT EXISTS `sla_hours` INT DEFAULT NULL COMMENT 'SLA时长(小时)' AFTER `priority`,
  ADD COLUMN IF NOT EXISTS `due_time` DATETIME DEFAULT NULL COMMENT '截止时间' AFTER `sla_hours`;
