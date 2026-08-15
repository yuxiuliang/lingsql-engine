ALTER TABLE `t_lingsql_engine_org_relation_execution`
  ADD COLUMN IF NOT EXISTS `escalation_status` VARCHAR(32) DEFAULT NULL COMMENT '升级状态' AFTER `due_time`,
  ADD COLUMN IF NOT EXISTS `escalation_level` INT DEFAULT NULL COMMENT '升级级别' AFTER `escalation_status`,
  ADD COLUMN IF NOT EXISTS `last_escalated_time` DATETIME DEFAULT NULL COMMENT '最近升级时间' AFTER `escalation_level`;
