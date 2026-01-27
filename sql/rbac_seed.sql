-- RBAC 初始化数据模板
-- 作者：余炘洋
-- 日期：2026-01-23

-- 租户与账号配置（请按实际修改）
SET @tenant_id = '1';
SET @admin_username = 'admin';
SET @experience_username = '测试';
-- 密码需使用 BCrypt 加密后写入  ***REDACTED-SECRET***
SET @password_hash = '{BCryptHashHere}';

-- 租户初始化
INSERT INTO t_lingsql_engine_tenant (tenant_id, tenant_name, status, is_deleted, quota_balance, service_level)
VALUES (@tenant_id, '默认租户', 1, 0, 0, 'FREE')
ON DUPLICATE KEY UPDATE
    tenant_name = VALUES(tenant_name),
    status = VALUES(status),
    is_deleted = VALUES(is_deleted),
    quota_balance = VALUES(quota_balance),
    service_level = VALUES(service_level);

-- 权限初始化
INSERT INTO t_lingsql_engine_permission (perm_code, perm_name, perm_type, status)
VALUES
('MENU:CHAT', '菜单-智能问答', 'MENU', 1),
('MENU:CHAT:HISTORY', '菜单-问答历史', 'MENU', 1),
('MENU:ADMIN', '菜单-管理后台', 'MENU', 1),
('MENU:ADMIN:TENANT', '菜单-租户管理', 'MENU', 1),
('MENU:ADMIN:MODEL', '菜单-模型管理', 'MENU', 1),
('MENU:ADMIN:SEMANTIC', '菜单-语义字典', 'MENU', 1),
('MENU:ADMIN:JOIN', '菜单-Join规则', 'MENU', 1),
('MENU:ADMIN:METADATA', '菜单-元数据', 'MENU', 1),
('MENU:ADMIN:EXPERIENCE', '菜单-体验账号', 'MENU', 1),
('MENU:ADMIN:USER', '菜单-用户管理', 'MENU', 1),
('MENU:ADMIN:ROLE', '菜单-角色管理', 'MENU', 1),
('MENU:ADMIN:PERMISSION', '菜单-权限管理', 'MENU', 1),
('MENU:ADMIN:AUDIT', '菜单-审计日志', 'MENU', 1),
('MENU:ADMIN:SQLLOG', '菜单-SQL日志', 'MENU', 1),
('API:CHAT:ASK', '接口-问答', 'API', 1),
('API:CHAT:HISTORY:READ', '接口-问答历史查询', 'API', 1),
('API:CHAT:HISTORY:WRITE', '接口-问答历史维护', 'API', 1),
('ADMIN:SCHEMA:SYNC', '接口-元数据同步', 'API', 1),
('ADMIN:SEMANTIC:REFRESH', '接口-语义缓存刷新', 'API', 1),
('ADMIN:MODEL:ENABLE', '接口-模型启用', 'API', 1),
('ADMIN:MODEL:DISABLE', '接口-模型禁用', 'API', 1),
('ADMIN:MODEL:HEALTH', '接口-模型健康', 'API', 1),
('ADMIN:SCHEMA:VIEW', '接口-元数据缓存查询', 'API', 1),
('ADMIN:TENANT:READ', '接口-租户读取', 'API', 1),
('ADMIN:TENANT:WRITE', '接口-租户维护', 'API', 1),
('ADMIN:TENANT:TEST', '接口-租户连接测试', 'API', 1),
('ADMIN:TENANT:DELETE', '接口-租户删除', 'API', 1),
('ADMIN:MODEL:READ', '接口-模型读取', 'API', 1),
('ADMIN:MODEL:WRITE', '接口-模型维护', 'API', 1),
('ADMIN:MODEL:DELETE', '接口-模型删除', 'API', 1),
('ADMIN:SEMANTIC:READ', '接口-语义读取', 'API', 1),
('ADMIN:SEMANTIC:WRITE', '接口-语义维护', 'API', 1),
('ADMIN:SEMANTIC:DELETE', '接口-语义删除', 'API', 1),
('ADMIN:JOIN:READ', '接口-Join规则读取', 'API', 1),
('ADMIN:JOIN:WRITE', '接口-Join规则维护', 'API', 1),
('ADMIN:JOIN:DELETE', '接口-Join规则删除', 'API', 1),
('ADMIN:METADATA:READ', '接口-元数据读取', 'API', 1),
('ADMIN:METADATA:WRITE', '接口-元数据维护', 'API', 1),
('ADMIN:METADATA:AI', '接口-元数据AI建议', 'API', 1),
('ADMIN:USER:READ', '接口-用户读取', 'API', 1),
('ADMIN:USER:WRITE', '接口-用户维护', 'API', 1),
('ADMIN:USER:RESET', '接口-用户重置密码', 'API', 1),
('ADMIN:ROLE:READ', '接口-角色读取', 'API', 1),
('ADMIN:ROLE:WRITE', '接口-角色维护', 'API', 1),
('ADMIN:PERM:READ', '接口-权限读取', 'API', 1),
('ADMIN:TENANT:POOL', '接口-租户权限池维护', 'API', 1),
('ADMIN:AUDIT:READ', '接口-审计日志读取', 'API', 1),
('ADMIN:SQLLOG:READ', '接口-SQL日志读取', 'API', 1),
('ADMIN:SQLLOG:EXPORT', '接口-SQL日志导出', 'API', 1),
('CONFIG:CHAT:READ', '接口-聊天配置读取', 'API', 1),
('RBAC:EXPERIENCE:READ', '接口-体验账号读取', 'API', 1),
('RBAC:EXPERIENCE:WRITE', '接口-体验账号维护', 'API', 1)
ON DUPLICATE KEY UPDATE
    perm_name = VALUES(perm_name),
    perm_type = VALUES(perm_type),
    status = VALUES(status);

-- 角色初始化
INSERT INTO t_lingsql_engine_role (tenant_id, role_code, role_name, status)
VALUES
(NULL, 'SUPER_ADMIN', '超级管理员', 1),
(NULL, 'SYS_ADMIN', '系统管理员', 1),
(NULL, 'ROLE_EXPERIENCE_TEMPLATE', '体验账号模板', 1),
(@tenant_id, 'TENANT_ADMIN', '租户管理员', 1)
ON DUPLICATE KEY UPDATE
    role_name = VALUES(role_name),
    status = VALUES(status);

-- 菜单初始化（全局菜单）
INSERT INTO t_lingsql_engine_menu (tenant_id, menu_code, menu_name, parent_id, path, component, icon, sort, visible, status, perm_code)
VALUES
(NULL, 'MENU_CHAT', '智能问答', NULL, '/chat', 'ChatPage', 'chat', 10, 1, 1, 'MENU:CHAT'),
(NULL, 'MENU_CHAT_HISTORY', '问答历史', NULL, '/chat/history', 'ChatHistory', 'chat', 20, 1, 1, 'MENU:CHAT:HISTORY'),
(NULL, 'MENU_ADMIN', '管理后台', NULL, '/layout', 'Layout', 'model', 100, 1, 1, 'MENU:ADMIN')
ON DUPLICATE KEY UPDATE
    menu_name = VALUES(menu_name),
    path = VALUES(path),
    component = VALUES(component),
    icon = VALUES(icon),
    sort = VALUES(sort),
    visible = VALUES(visible),
    status = VALUES(status),
    perm_code = VALUES(perm_code);

SET @menu_admin_id = (SELECT id FROM t_lingsql_engine_menu WHERE menu_code = 'MENU_ADMIN' AND tenant_id IS NULL LIMIT 1);

INSERT INTO t_lingsql_engine_menu (tenant_id, menu_code, menu_name, parent_id, path, component, icon, sort, visible, status, perm_code)
VALUES
(NULL, 'MENU_ADMIN_TENANT', '租户管理', @menu_admin_id, '/tenant', 'TenantManage', 'tenant', 110, 1, 1, 'MENU:ADMIN:TENANT'),
(NULL, 'MENU_ADMIN_DATASOURCE', '数据源管理', @menu_admin_id, '/datasource', 'TenantConfig', 'tenant', 115, 1, 1, 'MENU:ADMIN:TENANT'),
(NULL, 'MENU_ADMIN_MODEL', '模型资源池', @menu_admin_id, '/model', 'ModelConfig', 'model', 120, 1, 1, 'MENU:ADMIN:MODEL'),
(NULL, 'MENU_ADMIN_SEMANTIC', '语义字典', @menu_admin_id, '/lexicon', 'SemanticConfig', 'lexicon', 130, 1, 1, 'MENU:ADMIN:SEMANTIC'),
(NULL, 'MENU_ADMIN_JOIN', 'Join规则', @menu_admin_id, '/join-rule', 'JoinRule', 'join', 140, 1, 1, 'MENU:ADMIN:JOIN'),
(NULL, 'MENU_ADMIN_METADATA', '元数据', @menu_admin_id, '/metadata', 'Metadata', 'metadata', 150, 1, 1, 'MENU:ADMIN:METADATA'),
(NULL, 'MENU_ADMIN_EXPERIENCE', '体验账号', @menu_admin_id, '/experience', 'Experience', 'tenant', 160, 1, 1, 'MENU:ADMIN:EXPERIENCE'),
(NULL, 'MENU_ADMIN_USER', '用户管理', @menu_admin_id, '/user', 'UserManage', 'user', 170, 1, 1, 'MENU:ADMIN:USER'),
(NULL, 'MENU_ADMIN_ROLE', '角色管理', @menu_admin_id, '/role', 'RoleManage', 'role', 180, 1, 1, 'MENU:ADMIN:ROLE'),
(NULL, 'MENU_ADMIN_PERMISSION', '权限管理', @menu_admin_id, '/permission', 'PermissionManage', 'permission', 190, 1, 1, 'MENU:ADMIN:PERMISSION'),
(NULL, 'MENU_ADMIN_AUDIT', '审计日志', @menu_admin_id, '/audit', 'AuditLog', 'audit', 200, 1, 1, 'MENU:ADMIN:AUDIT'),
(NULL, 'MENU_ADMIN_SQLLOG', 'SQL日志', @menu_admin_id, '/sql-log', 'SqlLog', 'sql', 210, 1, 1, 'MENU:ADMIN:SQLLOG')
ON DUPLICATE KEY UPDATE
    menu_name = VALUES(menu_name),
    parent_id = VALUES(parent_id),
    path = VALUES(path),
    component = VALUES(component),
    icon = VALUES(icon),
    sort = VALUES(sort),
    visible = VALUES(visible),
    status = VALUES(status),
    perm_code = VALUES(perm_code);

-- 角色权限绑定
SET @sys_admin_role_id = (SELECT id FROM t_lingsql_engine_role WHERE role_code = 'SYS_ADMIN' AND tenant_id IS NULL LIMIT 1);
SET @super_admin_role_id = (SELECT id FROM t_lingsql_engine_role WHERE role_code = 'SUPER_ADMIN' AND tenant_id IS NULL LIMIT 1);
SET @tenant_admin_role_id = (SELECT id FROM t_lingsql_engine_role WHERE role_code = 'TENANT_ADMIN' AND tenant_id = @tenant_id LIMIT 1);
SET @experience_role_id = (SELECT id FROM t_lingsql_engine_role WHERE role_code = 'ROLE_EXPERIENCE_TEMPLATE' AND tenant_id IS NULL LIMIT 1);

INSERT IGNORE INTO t_lingsql_engine_role_permission (role_id, perm_id)
SELECT @sys_admin_role_id, id FROM t_lingsql_engine_permission;

INSERT IGNORE INTO t_lingsql_engine_role_permission (role_id, perm_id)
SELECT @super_admin_role_id, id FROM t_lingsql_engine_permission;

INSERT IGNORE INTO t_lingsql_engine_role_permission (role_id, perm_id)
SELECT @tenant_admin_role_id, id FROM t_lingsql_engine_permission;

INSERT IGNORE INTO t_lingsql_engine_role_permission (role_id, perm_id)
SELECT @experience_role_id, id FROM t_lingsql_engine_permission
WHERE perm_code IN (
    'MENU:CHAT',
    'MENU:CHAT:HISTORY',
    'API:CHAT:ASK',
    'API:CHAT:HISTORY:READ',
    'API:CHAT:HISTORY:WRITE',
    'CONFIG:CHAT:READ'
);

-- 数据权限配置
INSERT INTO t_lingsql_engine_role_data_scope (role_id, scope_type, scope_column, scope_rule, status)
SELECT @sys_admin_role_id, 'ALL', NULL, NULL, 1
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM t_lingsql_engine_role_data_scope WHERE role_id = @sys_admin_role_id
);

INSERT INTO t_lingsql_engine_role_data_scope (role_id, scope_type, scope_column, scope_rule, status)
SELECT @super_admin_role_id, 'ALL', NULL, NULL, 1
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM t_lingsql_engine_role_data_scope WHERE role_id = @super_admin_role_id
);

INSERT INTO t_lingsql_engine_role_data_scope (role_id, scope_type, scope_column, scope_rule, status)
SELECT @tenant_admin_role_id, 'TENANT', NULL, NULL, 1
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM t_lingsql_engine_role_data_scope WHERE role_id = @tenant_admin_role_id
);

INSERT INTO t_lingsql_engine_role_data_scope (role_id, scope_type, scope_column, scope_rule, status)
SELECT @experience_role_id, 'TENANT', NULL, NULL, 1
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM t_lingsql_engine_role_data_scope WHERE role_id = @experience_role_id
);

-- 初始化用户
INSERT INTO t_lingsql_engine_user (tenant_id, username, password, nickname, status, is_experience)
VALUES
(@tenant_id, @admin_username, @password_hash, '管理员', 1, 0),
(@tenant_id, @experience_username, @password_hash, '体验账号', 1, 1)
ON DUPLICATE KEY UPDATE
    password = VALUES(password),
    nickname = VALUES(nickname),
    status = VALUES(status),
    is_experience = VALUES(is_experience);

SET @admin_user_id = (SELECT id FROM t_lingsql_engine_user WHERE tenant_id = @tenant_id AND username = @admin_username LIMIT 1);
SET @experience_user_id = (SELECT id FROM t_lingsql_engine_user WHERE tenant_id = @tenant_id AND username = @experience_username LIMIT 1);

INSERT IGNORE INTO t_lingsql_engine_user_role (user_id, role_id)
VALUES
(@admin_user_id, @sys_admin_role_id),
(@admin_user_id, @tenant_admin_role_id),
(@experience_user_id, @experience_role_id);

-- 初始化租户权限池（默认分配全部权限）
INSERT IGNORE INTO t_lingsql_engine_tenant_permission (tenant_id, perm_id)
SELECT @tenant_id, id FROM t_lingsql_engine_permission;

-- 体验账号策略示例（可按需修改）
INSERT INTO t_lingsql_engine_user_experience_policy (user_id, menu_whitelist, data_scope_override, status)
VALUES
(@experience_user_id, '["MENU_CHAT","MENU_CHAT_HISTORY"]', '{"scopeType":"TENANT"}', 1)
ON DUPLICATE KEY UPDATE
    menu_whitelist = VALUES(menu_whitelist),
    data_scope_override = VALUES(data_scope_override),
    status = VALUES(status);

ALTER TABLE t_lingsql_engine_menu
    ADD COLUMN tenant_key varchar(64)
        GENERATED ALWAYS AS (IFNULL(tenant_id,'GLOBAL')) STORED COMMENT '租户唯一键(兼容NULL)',
    ADD UNIQUE KEY uk_menu_tenant_key (tenant_key, menu_code);
