package com.lingsql.rbac.init;

import com.lingsql.billing.BillingPolicy;
import com.lingsql.config.LingSqlProperties;
import com.lingsql.rbac.entity.RbacPermission;
import com.lingsql.rbac.entity.RbacRole;
import com.lingsql.rbac.entity.RbacRolePermission;
import com.lingsql.rbac.entity.RbacUser;
import com.lingsql.rbac.entity.RbacUserRole;
import com.lingsql.rbac.entity.RbacMenu;
import com.lingsql.rbac.repository.RbacMenuRepository;
import com.lingsql.rbac.repository.RbacPermissionRepository;
import com.lingsql.rbac.repository.RbacRolePermissionRepository;
import com.lingsql.rbac.repository.RbacRoleRepository;
import com.lingsql.rbac.repository.RbacUserRepository;
import com.lingsql.rbac.repository.RbacUserRoleRepository;
import com.lingsql.tenant.entity.TenantInfo;
import com.lingsql.tenant.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.HashMap;
import java.util.stream.Collectors;

/**
 * 超级管理员初始化器。
 *
 * @author 余炘洋
 * @date 2026-01-27
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SuperAdminInitializer implements ApplicationRunner {

    private static final String SUPER_ADMIN_ROLE_CODE = "SUPER_ADMIN";
    private static final String SUPER_ADMIN_ROLE_NAME = "超级管理员";
    private static final int ACTIVE_STATUS = 1;
    private static final int NOT_DELETED = 0;

    private static final List<PermissionSeed> PERMISSION_SEEDS = List.of(
            new PermissionSeed("MENU:CHAT", "菜单-智能问答", "MENU", 1),
            new PermissionSeed("MENU:CHAT:HISTORY", "菜单-问答历史", "MENU", 1),
            new PermissionSeed("MENU:ADMIN", "菜单-管理后台", "MENU", 1),
            new PermissionSeed("MENU:ADMIN:TENANT", "菜单-租户管理", "MENU", 1),
            new PermissionSeed("MENU:ADMIN:MODEL", "菜单-模型管理", "MENU", 1),
            new PermissionSeed("MENU:ADMIN:SEMANTIC", "菜单-语义字典", "MENU", 1),
            new PermissionSeed("MENU:ADMIN:JOIN", "菜单-Join规则", "MENU", 1),
            new PermissionSeed("MENU:ADMIN:METADATA", "菜单-元数据", "MENU", 1),
            new PermissionSeed("MENU:ADMIN:EXPERIENCE", "菜单-体验账号", "MENU", 1),
            new PermissionSeed("MENU:ADMIN:USER", "菜单-用户管理", "MENU", 1),
            new PermissionSeed("MENU:ADMIN:ROLE", "菜单-角色管理", "MENU", 1),
            new PermissionSeed("MENU:ADMIN:PERMISSION", "菜单-权限管理", "MENU", 1),
            new PermissionSeed("MENU:ADMIN:AUDIT", "菜单-审计日志", "MENU", 1),
            new PermissionSeed("MENU:ADMIN:SQLLOG", "菜单-SQL日志", "MENU", 1),
            new PermissionSeed("API:CHAT:ASK", "接口-问答", "API", 1),
            new PermissionSeed("API:CHAT:HISTORY:READ", "接口-问答历史查询", "API", 1),
            new PermissionSeed("API:CHAT:HISTORY:WRITE", "接口-问答历史维护", "API", 1),
            new PermissionSeed("ADMIN:SCHEMA:SYNC", "接口-元数据同步", "API", 1),
            new PermissionSeed("ADMIN:SEMANTIC:REFRESH", "接口-语义缓存刷新", "API", 1),
            new PermissionSeed("ADMIN:MODEL:ENABLE", "接口-模型启用", "API", 1),
            new PermissionSeed("ADMIN:MODEL:DISABLE", "接口-模型禁用", "API", 1),
            new PermissionSeed("ADMIN:MODEL:HEALTH", "接口-模型健康", "API", 1),
            new PermissionSeed("ADMIN:SCHEMA:VIEW", "接口-元数据缓存查询", "API", 1),
            new PermissionSeed("ADMIN:TENANT:READ", "接口-租户读取", "API", 1),
            new PermissionSeed("ADMIN:TENANT:WRITE", "接口-租户维护", "API", 1),
            new PermissionSeed("ADMIN:TENANT:TEST", "接口-租户连接测试", "API", 1),
            new PermissionSeed("ADMIN:TENANT:DELETE", "接口-租户删除", "API", 1),
            new PermissionSeed("ADMIN:MODEL:READ", "接口-模型读取", "API", 1),
            new PermissionSeed("ADMIN:MODEL:WRITE", "接口-模型维护", "API", 1),
            new PermissionSeed("ADMIN:MODEL:DELETE", "接口-模型删除", "API", 1),
            new PermissionSeed("ADMIN:SEMANTIC:READ", "接口-语义读取", "API", 1),
            new PermissionSeed("ADMIN:SEMANTIC:WRITE", "接口-语义维护", "API", 1),
            new PermissionSeed("ADMIN:SEMANTIC:DELETE", "接口-语义删除", "API", 1),
            new PermissionSeed("ADMIN:JOIN:READ", "接口-Join规则读取", "API", 1),
            new PermissionSeed("ADMIN:JOIN:WRITE", "接口-Join规则维护", "API", 1),
            new PermissionSeed("ADMIN:JOIN:DELETE", "接口-Join规则删除", "API", 1),
            new PermissionSeed("ADMIN:METADATA:READ", "接口-元数据读取", "API", 1),
            new PermissionSeed("ADMIN:METADATA:WRITE", "接口-元数据维护", "API", 1),
            new PermissionSeed("ADMIN:METADATA:AI", "接口-元数据AI建议", "API", 1),
            new PermissionSeed("ADMIN:USER:READ", "接口-用户读取", "API", 1),
            new PermissionSeed("ADMIN:USER:WRITE", "接口-用户维护", "API", 1),
            new PermissionSeed("ADMIN:USER:RESET", "接口-用户重置密码", "API", 1),
            new PermissionSeed("ADMIN:ROLE:READ", "接口-角色读取", "API", 1),
            new PermissionSeed("ADMIN:ROLE:WRITE", "接口-角色维护", "API", 1),
            new PermissionSeed("ADMIN:BILLING:READ", "接口-计费读取", "API", 1),
            new PermissionSeed("ADMIN:BILLING:WRITE", "接口-计费维护", "API", 1),
            new PermissionSeed("ADMIN:PERM:READ", "接口-权限读取", "API", 1),
            new PermissionSeed("ADMIN:TENANT:POOL", "接口-租户权限池维护", "API", 1),
            new PermissionSeed("ADMIN:AUDIT:READ", "接口-审计日志读取", "API", 1),
            new PermissionSeed("ADMIN:SQLLOG:READ", "接口-SQL日志读取", "API", 1),
            new PermissionSeed("ADMIN:SQLLOG:EXPORT", "接口-SQL日志导出", "API", 1),
            new PermissionSeed("CONFIG:CHAT:READ", "接口-聊天配置读取", "API", 1),
            new PermissionSeed("RBAC:EXPERIENCE:READ", "接口-体验账号读取", "API", 1),
            new PermissionSeed("RBAC:EXPERIENCE:WRITE", "接口-体验账号维护", "API", 1)
    );

    private static final List<MenuSeed> MENU_SEEDS = List.of(
            new MenuSeed("MENU_CHAT", "智能问答", null, "/chat", "ChatPage", "chat", 10, 1, 1, "MENU:CHAT"),
            new MenuSeed("MENU_CHAT_HISTORY", "问答历史", null, "/chat/history", "ChatHistory", "chat", 20, 1, 1, "MENU:CHAT:HISTORY"),
            new MenuSeed("MENU_ADMIN", "管理后台", null, "/layout", "Layout", "model", 100, 1, 1, "MENU:ADMIN"),
            new MenuSeed("MENU_ADMIN_TENANT", "租户管理", "MENU_ADMIN", "/tenant", "TenantManage", "tenant", 110, 1, 1, "MENU:ADMIN:TENANT"),
            new MenuSeed("MENU_ADMIN_DATASOURCE", "数据源管理", "MENU_ADMIN", "/datasource", "TenantConfig", "tenant", 115, 1, 1, "MENU:ADMIN:TENANT"),
            new MenuSeed("MENU_ADMIN_MODEL", "模型资源池", "MENU_ADMIN", "/model", "ModelConfig", "model", 120, 1, 1, "MENU:ADMIN:MODEL"),
            new MenuSeed("MENU_ADMIN_SEMANTIC", "语义字典", "MENU_ADMIN", "/lexicon", "SemanticConfig", "lexicon", 130, 1, 1, "MENU:ADMIN:SEMANTIC"),
            new MenuSeed("MENU_ADMIN_JOIN", "Join规则", "MENU_ADMIN", "/join-rule", "JoinRule", "join", 140, 1, 1, "MENU:ADMIN:JOIN"),
            new MenuSeed("MENU_ADMIN_METADATA", "元数据", "MENU_ADMIN", "/metadata", "Metadata", "metadata", 150, 1, 1, "MENU:ADMIN:METADATA"),
            new MenuSeed("MENU_ADMIN_EXPERIENCE", "体验账号", "MENU_ADMIN", "/experience", "Experience", "tenant", 160, 1, 1, "MENU:ADMIN:EXPERIENCE"),
            new MenuSeed("MENU_ADMIN_USER", "用户管理", "MENU_ADMIN", "/user", "UserManage", "user", 170, 1, 1, "MENU:ADMIN:USER"),
            new MenuSeed("MENU_ADMIN_ROLE", "角色管理", "MENU_ADMIN", "/role", "RoleManage", "role", 180, 1, 1, "MENU:ADMIN:ROLE"),
            new MenuSeed("MENU_ADMIN_PERMISSION", "权限管理", "MENU_ADMIN", "/permission", "PermissionManage", "permission", 190, 1, 1, "MENU:ADMIN:PERMISSION"),
            new MenuSeed("MENU_ADMIN_AUDIT", "审计日志", "MENU_ADMIN", "/audit", "AuditLog", "audit", 200, 1, 1, "MENU:ADMIN:AUDIT"),
            new MenuSeed("MENU_ADMIN_SQLLOG", "SQL日志", "MENU_ADMIN", "/sql-log", "SqlLog", "sql", 210, 1, 1, "MENU:ADMIN:SQLLOG")
    );

    private final LingSqlProperties properties;
    private final TenantRepository tenantRepository;
    private final RbacUserRepository userRepository;
    private final RbacRoleRepository roleRepository;
    private final RbacPermissionRepository permissionRepository;
    private final RbacRolePermissionRepository rolePermissionRepository;
    private final RbacUserRoleRepository userRoleRepository;
    private final RbacMenuRepository menuRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        LingSqlProperties.Admin admin = properties.getAdmin();
        if (admin == null || !admin.isEnabled()) {
            return;
        }
        String tenantId = normalize(admin.getTenantId());
        String username = normalize(admin.getUsername());
        String password = admin.getPassword();
        if (!StringUtils.hasText(tenantId) || !StringUtils.hasText(username)) {
            log.warn("超级管理员初始化失败: tenantId 或 username 为空");
            return;
        }
        if (!StringUtils.hasText(password)) {
            log.warn("超级管理员初始化失败: 密码为空");
            return;
        }
        if (!validatePassword(password)) {
            log.warn("超级管理员初始化失败: 密码强度不足(需8-32位且包含字母与数字)");
            return;
        }
        ensurePermissions();
        ensureMenus();
        ensureTenantExists(tenantId, admin.getTenantName());
        RbacRole superAdminRole = ensureSuperAdminRole();
        if (superAdminRole == null || superAdminRole.getId() == null) {
            log.warn("超级管理员初始化失败: 角色创建失败");
            return;
        }
        RbacUser superAdminUser = ensureSuperAdminUser(tenantId, username, password, admin.getNickname());
        if (superAdminUser == null || superAdminUser.getId() == null) {
            return;
        }
        bindUserRole(superAdminUser.getId(), superAdminRole.getId());
        bindRolePermissions(superAdminRole.getId());
    }

    private void ensureTenantExists(String tenantId, String tenantName) {
        if (tenantRepository.findByTenantIdAndIsDeleted(tenantId, NOT_DELETED).isPresent()) {
            return;
        }
        TenantInfo tenant = new TenantInfo();
        tenant.setTenantId(tenantId);
        tenant.setTenantName(StringUtils.hasText(tenantName) ? tenantName.trim() : "系统租户");
        tenant.setStatus(ACTIVE_STATUS);
        tenant.setRemark("系统初始化");
        tenant.setPlanName("SYSTEM");
        tenant.setQuotaBalance(0L);
        tenant.setServiceLevel(BillingPolicy.DEFAULT_SERVICE_LEVEL);
        tenant.setIsDeleted(NOT_DELETED);
        tenant.setCreateTime(LocalDateTime.now());
        tenant.setUpdateTime(LocalDateTime.now());
        try {
            tenantRepository.save(tenant);
        } catch (DataIntegrityViolationException ex) {
            log.warn("租户初始化冲突: {}", ex.getMessage());
        }
    }

    private RbacRole ensureSuperAdminRole() {
        RbacRole existing = roleRepository.findByTenantIdIsNullAndRoleCode(SUPER_ADMIN_ROLE_CODE).orElse(null);
        if (existing != null) {
            return existing;
        }
        RbacRole role = new RbacRole();
        role.setTenantId(null);
        role.setRoleCode(SUPER_ADMIN_ROLE_CODE);
        role.setRoleName(SUPER_ADMIN_ROLE_NAME);
        role.setStatus(ACTIVE_STATUS);
        role.setCreateTime(LocalDateTime.now());
        role.setUpdateTime(LocalDateTime.now());
        try {
            return roleRepository.save(role);
        } catch (DataIntegrityViolationException ex) {
            log.warn("超级管理员角色已存在: {}", ex.getMessage());
            return roleRepository.findByTenantIdIsNullAndRoleCode(SUPER_ADMIN_ROLE_CODE).orElse(null);
        }
    }

    private RbacUser ensureSuperAdminUser(String tenantId, String username, String rawPassword, String nickname) {
        RbacUser existing = userRepository.findByTenantIdAndUsername(tenantId, username).orElse(null);
        if (existing != null) {
            return existing;
        }
        List<RbacUser> byName = userRepository.findTop2ByUsername(username);
        if (!CollectionUtils.isEmpty(byName)) {
            log.warn("超级管理员初始化跳过: 账号已存在于其他租户 username={}", username);
            return null;
        }
        RbacUser user = new RbacUser();
        user.setTenantId(tenantId);
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setNickname(StringUtils.hasText(nickname) ? nickname.trim() : "超级管理员");
        user.setStatus(ACTIVE_STATUS);
        user.setIsExperience(0);
        user.setCreateTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());
        try {
            return userRepository.save(user);
        } catch (DataIntegrityViolationException ex) {
            log.warn("超级管理员用户已存在: {}", ex.getMessage());
            return userRepository.findByTenantIdAndUsername(tenantId, username).orElse(null);
        }
    }

    private void bindUserRole(Long userId, Long roleId) {
        if (userId == null || roleId == null) {
            return;
        }
        List<Long> roleIds = userRoleRepository.findRoleIdsByUserId(userId);
        if (roleIds.contains(roleId)) {
            return;
        }
        RbacUserRole relation = new RbacUserRole();
        relation.setUserId(userId);
        relation.setRoleId(roleId);
        relation.setCreateTime(LocalDateTime.now());
        try {
            userRoleRepository.save(relation);
        } catch (DataIntegrityViolationException ex) {
            log.warn("超级管理员角色绑定冲突: {}", ex.getMessage());
        }
    }

    private void bindRolePermissions(Long roleId) {
        if (roleId == null) {
            return;
        }
        List<RbacPermission> permissions = permissionRepository.findAll();
        if (CollectionUtils.isEmpty(permissions)) {
            log.warn("超级管理员权限初始化跳过: 权限表为空");
            return;
        }
        List<Long> existing = rolePermissionRepository.findPermIdsByRoleIds(List.of(roleId));
        Set<Long> existingSet = existing == null ? new HashSet<>() : new HashSet<>(existing);
        List<RbacRolePermission> relations = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        for (RbacPermission permission : permissions) {
            if (permission.getId() == null || existingSet.contains(permission.getId())) {
                continue;
            }
            RbacRolePermission relation = new RbacRolePermission();
            relation.setRoleId(roleId);
            relation.setPermId(permission.getId());
            relation.setCreateTime(now);
            relations.add(relation);
        }
        if (relations.isEmpty()) {
            return;
        }
        try {
            rolePermissionRepository.saveAll(relations);
        } catch (DataIntegrityViolationException ex) {
            log.warn("超级管理员权限绑定冲突: {}", ex.getMessage());
        }
    }

    private void ensurePermissions() {
        LocalDateTime now = LocalDateTime.now();
        List<RbacPermission> toSave = new ArrayList<>();
        for (PermissionSeed seed : PERMISSION_SEEDS) {
            if (!StringUtils.hasText(seed.permCode)) {
                continue;
            }
            if (permissionRepository.findByPermCode(seed.permCode).isPresent()) {
                continue;
            }
            RbacPermission permission = new RbacPermission();
            permission.setPermCode(seed.permCode);
            permission.setPermName(seed.permName);
            permission.setPermType(seed.permType);
            permission.setStatus(seed.status);
            permission.setCreateTime(now);
            permission.setUpdateTime(now);
            toSave.add(permission);
        }
        if (toSave.isEmpty()) {
            return;
        }
        try {
            permissionRepository.saveAll(toSave);
        } catch (DataIntegrityViolationException ex) {
            log.warn("权限初始化冲突: {}", ex.getMessage());
        }
    }

    private void ensureMenus() {
        List<RbacMenu> existingMenus = menuRepository.findByTenantIdIsNull();
        Map<String, RbacMenu> menuMap = existingMenus.stream()
                .filter(menu -> StringUtils.hasText(menu.getMenuCode()))
                .collect(Collectors.toMap(RbacMenu::getMenuCode, menu -> menu, (a, b) -> a, HashMap::new));
        LocalDateTime now = LocalDateTime.now();
        for (MenuSeed seed : MENU_SEEDS) {
            if (!StringUtils.hasText(seed.menuCode)) {
                continue;
            }
            if (menuMap.containsKey(seed.menuCode)) {
                continue;
            }
            Long parentId = null;
            if (StringUtils.hasText(seed.parentCode)) {
                RbacMenu parent = menuMap.get(seed.parentCode);
                if (parent != null) {
                    parentId = parent.getId();
                } else {
                    log.warn("菜单初始化跳过，父级不存在: {}", seed.menuCode);
                    continue;
                }
            }
            RbacMenu menu = new RbacMenu();
            menu.setTenantId(null);
            menu.setMenuCode(seed.menuCode);
            menu.setMenuName(seed.menuName);
            menu.setParentId(parentId);
            menu.setPath(seed.path);
            menu.setComponent(seed.component);
            menu.setIcon(seed.icon);
            menu.setSort(seed.sort);
            menu.setVisible(seed.visible);
            menu.setStatus(seed.status);
            menu.setPermCode(seed.permCode);
            menu.setCreateTime(now);
            menu.setUpdateTime(now);
            try {
                RbacMenu saved = menuRepository.save(menu);
                menuMap.put(saved.getMenuCode(), saved);
            } catch (DataIntegrityViolationException ex) {
                log.warn("菜单初始化冲突: {}", ex.getMessage());
            }
        }
    }

    private boolean validatePassword(String password) {
        if (!StringUtils.hasText(password)) {
            return false;
        }
        String trimmed = password.trim();
        if (trimmed.length() < 8 || trimmed.length() > 32) {
            return false;
        }
        boolean hasLetter = trimmed.chars().anyMatch(Character::isLetter);
        boolean hasDigit = trimmed.chars().anyMatch(Character::isDigit);
        return hasLetter && hasDigit;
    }

    private String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private record PermissionSeed(String permCode, String permName, String permType, Integer status) {
    }

    private record MenuSeed(String menuCode, String menuName, String parentCode, String path, String component,
                            String icon, Integer sort, Integer visible, Integer status, String permCode) {
    }
}
