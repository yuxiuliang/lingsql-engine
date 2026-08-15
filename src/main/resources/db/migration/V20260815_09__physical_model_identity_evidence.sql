-- 为每次物理模型调用冻结实际配置、版本和选择时路由指纹。
-- 历史调用无法可靠反推出原始配置版本，兼容迁移保持 NULL；新版本应用会拒绝缺少身份的调用。

ALTER TABLE `t_lingsql_engine_billing_invocation`
    ADD COLUMN `model_config_id` bigint DEFAULT NULL COMMENT '实际模型配置ID',
    ADD COLUMN `model_version` varchar(128) DEFAULT NULL COMMENT '调用时模型版本',
    ADD COLUMN `route_fingerprint` char(64) DEFAULT NULL COMMENT '调用选择时路由配置SHA-256',
    ADD KEY `idx_billing_invocation_model_config` (`model_config_id`),
    ADD KEY `idx_billing_invocation_route_fingerprint` (`route_fingerprint`);
