-- 持久化模型真实调用验证状态，防止重启后丢失可用性证据。

ALTER TABLE `t_lingsql_engine_ai_model_config`
    ADD COLUMN `last_probe_status` varchar(16) DEFAULT NULL COMMENT '最近验证状态：HEALTHY/FAILED' AFTER `fuse_reason`,
    ADD COLUMN `last_probe_time` datetime DEFAULT NULL COMMENT '最近验证时间' AFTER `last_probe_status`,
    ADD COLUMN `last_probe_cost_ms` bigint DEFAULT NULL COMMENT '最近验证耗时毫秒' AFTER `last_probe_time`,
    ADD COLUMN `last_probe_error_code` varchar(64) DEFAULT NULL COMMENT '最近验证归一化错误码' AFTER `last_probe_cost_ms`;
