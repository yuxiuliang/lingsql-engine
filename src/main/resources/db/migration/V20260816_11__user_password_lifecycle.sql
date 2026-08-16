ALTER TABLE `t_lingsql_engine_user`
    ADD COLUMN `password_state` varchar(24) NOT NULL DEFAULT 'ACTIVE'
        COMMENT '密码状态: ACTIVE-可登录, CHANGE_REQUIRED-必须先改密' AFTER `password`,
    ADD COLUMN `credential_version` bigint NOT NULL DEFAULT 1
        COMMENT '凭据版本，密码设置或修改后递增' AFTER `password_state`,
    ADD COLUMN `password_changed_at` datetime DEFAULT NULL
        COMMENT '当前密码由用户确认完成的时间' AFTER `credential_version`;
