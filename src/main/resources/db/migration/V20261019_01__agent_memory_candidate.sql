-- Agent 长期记忆候选闭环：模型只能产出候选事实（CANDIDATE，不生效），
-- 采纳必须经用户确认或管理端批准另建生效记录，保留期到期由任务转为 EXPIRED。
ALTER TABLE `t_lingsql_engine_agent_memory_fact`
    MODIFY COLUMN `source_type` varchar(32) NOT NULL COMMENT '来源：USER_CONFIRMED 用户确认 / ADMIN_APPROVED 管理端批准 / MODEL_CANDIDATE 模型候选（仅存候选池，不生效）',
    MODIFY COLUMN `status` varchar(32) NOT NULL COMMENT '状态：ACTIVE 生效 / CANDIDATE 候选待确认 / APPROVED 候选已采纳 / REJECTED 候选已拒绝 / SUPERSEDED 已被新版本取代 / REVOKED 已撤销 / EXPIRED 已过保留期',
    ADD COLUMN `source_ref` varchar(128) DEFAULT NULL COMMENT '候选来源标识（会话或请求标识）' AFTER `created_by`,
    ADD COLUMN `sensitivity` varchar(32) NOT NULL DEFAULT 'NORMAL' COMMENT '敏感分类：NORMAL 普通 / SENSITIVE 敏感（敏感候选不允许采纳）' AFTER `source_ref`,
    ADD COLUMN `reviewed_by` varchar(64) DEFAULT NULL COMMENT '候选复核人标识' AFTER `sensitivity`,
    ADD COLUMN `reviewed_at` datetime DEFAULT NULL COMMENT '候选复核时间' AFTER `reviewed_by`,
    ADD COLUMN `approved_memory_id` bigint DEFAULT NULL COMMENT '采纳后生成的生效记忆主键' AFTER `reviewed_at`,
    ADD COLUMN `reject_reason` varchar(256) DEFAULT NULL COMMENT '候选拒绝原因' AFTER `approved_memory_id`,
    ADD KEY `idx_agent_memory_candidate` (`tenant_id`, `user_id`, `status`, `updated_at`);
