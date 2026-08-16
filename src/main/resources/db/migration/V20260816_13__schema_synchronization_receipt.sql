ALTER TABLE `t_lingsql_engine_tenant_db_config`
    ADD COLUMN `schema_table_count` int DEFAULT NULL COMMENT '最近一次成功同步的实际表数',
    ADD COLUMN `schema_whitelist_table_count` int DEFAULT NULL COMMENT '最近一次成功同步的白名单表数',
    ADD COLUMN `schema_whitelist_digest` varchar(64) DEFAULT NULL COMMENT '规范化白名单SHA-256',
    ADD COLUMN `schema_sync_token` varchar(36) DEFAULT NULL COMMENT '同步并发令牌';
