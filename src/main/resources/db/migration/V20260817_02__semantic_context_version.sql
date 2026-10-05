CREATE TABLE `t_lingsql_engine_semantic_context_version` (
    `id`          bigint      NOT NULL AUTO_INCREMENT,
    `tenant_id`   varchar(64) NOT NULL COMMENT '租户标识',
    `version_id`  varchar(64) NOT NULL COMMENT '语义版本号',
    `digest`      varchar(128) DEFAULT NULL COMMENT '摘要',
    `published_at` datetime   NOT NULL COMMENT '发布时间',
    PRIMARY KEY (`id`),
    KEY `idx_tenant_published` (`tenant_id`, `published_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='语义上下文版本快照';
