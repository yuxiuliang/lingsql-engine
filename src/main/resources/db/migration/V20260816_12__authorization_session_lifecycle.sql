ALTER TABLE `t_lingsql_engine_tenant`
    ADD COLUMN `authorization_version` bigint NOT NULL DEFAULT 1 COMMENT '租户授权事实版本';

ALTER TABLE `t_lingsql_engine_user`
    ADD COLUMN `authorization_version` bigint NOT NULL DEFAULT 1 COMMENT '用户授权事实版本';

ALTER TABLE `t_lingsql_engine_role`
    ADD COLUMN `authorization_version` bigint NOT NULL DEFAULT 1 COMMENT '角色授权事实版本';
