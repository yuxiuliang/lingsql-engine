-- Agent 长期记忆事实：承载用户永久记忆与租户程序记忆，写入必须经过敏感信息与注入校验
CREATE TABLE `t_lingsql_engine_agent_memory_fact` (
    `id`            bigint       NOT NULL AUTO_INCREMENT,
    `tenant_id`     varchar(64)  NOT NULL COMMENT '租户标识',
    `user_id`       bigint       DEFAULT NULL COMMENT '用户标识，为空表示租户级记忆',
    `tier`          varchar(32)  NOT NULL COMMENT '记忆层级：PROCEDURAL 程序记忆 / PERMANENT 永久记忆',
    `fact_text`     varchar(500) NOT NULL COMMENT '记忆事实正文',
    `source_type`   varchar(32)  NOT NULL COMMENT '来源：USER_CONFIRMED 用户确认 / ADMIN_APPROVED 管理端批准',
    `status`        varchar(32)  NOT NULL COMMENT '状态：ACTIVE 生效 / SUPERSEDED 已被新版本取代 / REVOKED 已撤销',
    `confidence`    int          NOT NULL COMMENT '置信度分值 0-100',
    `version`       int          NOT NULL COMMENT '版本号，纠正生成更高版本',
    `supersedes_id` bigint       DEFAULT NULL COMMENT '被当前版本取代的旧记忆主键',
    `created_by`    varchar(64)  DEFAULT NULL COMMENT '写入人标识',
    `expires_at`    datetime     DEFAULT NULL COMMENT '保留期到期时间，为空表示不自动过期',
    `created_at`    datetime     NOT NULL COMMENT '创建时间',
    `updated_at`    datetime     NOT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_agent_memory_scope` (`tenant_id`, `user_id`, `tier`, `status`),
    KEY `idx_agent_memory_expire` (`status`, `expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent 长期记忆事实';
