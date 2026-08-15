-- LingSQL 商业化基线种子数据
-- 目标：
-- 1) 保证至少存在一个可售启用套餐，避免 readiness 因套餐为空失败。
-- 2) 给试点/标准客户提供可复用的默认套餐模板。

INSERT INTO `t_lingsql_engine_billing_plan`
(`plan_code`, `plan_name`, `status`, `service_level`, `tenant_type`, `default_quota_balance`, `remark`, `is_deleted`)
VALUES
('PLAN_FREE', '免费版', 1, 'FREE', 'TRIAL', 10000, '用于试用环境，限制规模', 0),
('PLAN_BASIC', '标准版', 1, 'BASIC', 'SMB', 500000, '中小团队默认套餐', 0),
('PLAN_PRO', '专业版', 1, 'PRO', 'ENTERPRISE', 3000000, '生产环境推荐套餐', 0)
ON DUPLICATE KEY UPDATE
  `plan_name` = VALUES(`plan_name`),
  `status` = VALUES(`status`),
  `service_level` = VALUES(`service_level`),
  `tenant_type` = VALUES(`tenant_type`),
  `default_quota_balance` = VALUES(`default_quota_balance`),
  `remark` = VALUES(`remark`),
  `is_deleted` = VALUES(`is_deleted`),
  `update_time` = CURRENT_TIMESTAMP;

-- 数字员工模板（用于演示签单）
INSERT INTO `t_lingsql_engine_digital_employee`
(`tenant_id`, `employee_code`, `employee_name`, `role_name`, `scenario`, `prompt_template`,
 `default_question`, `status`, `run_count`, `success_count`, `remark`, `is_deleted`)
VALUES
('sys_tenant', 'EMP_SALES_DAILY', '销售日报助理', '销售分析师', '日报分析',
 '请将结果整理为“核心指标+异常提示+下一步建议”三段结构。', '统计昨日销售额、订单数和客单价', 1, 0, 0, '商业演示模板', 0),
('sys_tenant', 'EMP_RISK_ALERT', '经营风险预警员', '运营分析师', '风险巡检',
 '请重点关注环比下滑超过15%的指标，并给出两条行动建议。', '统计近7天销售额、利润率和退货率变化趋势', 1, 0, 0, '商业演示模板', 0)
ON DUPLICATE KEY UPDATE
  `employee_name` = VALUES(`employee_name`),
  `role_name` = VALUES(`role_name`),
  `scenario` = VALUES(`scenario`),
  `prompt_template` = VALUES(`prompt_template`),
  `default_question` = VALUES(`default_question`),
  `status` = VALUES(`status`),
  `remark` = VALUES(`remark`),
  `is_deleted` = VALUES(`is_deleted`),
  `update_time` = CURRENT_TIMESTAMP;
