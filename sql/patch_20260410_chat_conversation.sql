-- 会话维度补丁 (2026-04-10)
-- 将聊天历史从“单条问答”升级为“按会话归档”。

ALTER TABLE `t_lingsql_engine_chat_history`
    ADD COLUMN IF NOT EXISTS `conversation_id` varchar(64) DEFAULT NULL COMMENT '会话ID' AFTER `user_id`,
    ADD COLUMN IF NOT EXISTS `conversation_title` text COMMENT '会话标题' AFTER `conversation_id`;

ALTER TABLE `t_lingsql_engine_chat_history`
    ADD KEY `idx_chat_history_conversation` (`tenant_id`, `user_id`, `conversation_id`);

UPDATE `t_lingsql_engine_chat_history`
SET `conversation_id` = CONCAT('legacy-', `id`)
WHERE `conversation_id` IS NULL
   OR `conversation_id` = '';

UPDATE `t_lingsql_engine_chat_history`
SET `conversation_title` = COALESCE(NULLIF(`conversation_title`, ''), `question`)
WHERE `conversation_title` IS NULL
   OR `conversation_title` = '';
