CREATE TABLE `t_lingsql_engine_knowledge_document` (
    `id`         bigint       NOT NULL AUTO_INCREMENT,
    `tenant_id`  varchar(64)  NOT NULL COMMENT '租户标识',
    `title`      varchar(256) NOT NULL COMMENT '标题',
    `content`    text         DEFAULT NULL COMMENT '内容',
    `version`    varchar(32)  DEFAULT NULL COMMENT '版本',
    `status`     varchar(32)  NOT NULL COMMENT '状态',
    `created_at` datetime     NOT NULL COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_tenant_status` (`tenant_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库文档';
