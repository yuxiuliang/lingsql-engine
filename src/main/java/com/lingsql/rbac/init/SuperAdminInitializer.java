package com.lingsql.rbac.init;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingsql.billing.BillingBalanceService;
import com.lingsql.billing.entity.BillingPlan;
import com.lingsql.billing.BillingPolicy;
import com.lingsql.billing.repository.BillingPlanRepository;
import com.lingsql.config.LingSqlProperties;
import com.lingsql.rbac.entity.RbacMenu;
import com.lingsql.rbac.entity.RbacPermission;
import com.lingsql.rbac.entity.RbacRole;
import com.lingsql.rbac.entity.RbacRolePermission;
import com.lingsql.rbac.entity.RbacTenantPermission;
import com.lingsql.rbac.entity.RbacUser;
import com.lingsql.rbac.entity.RbacUserRole;
import com.lingsql.rbac.repository.RbacMenuRepository;
import com.lingsql.rbac.repository.RbacPermissionRepository;
import com.lingsql.rbac.repository.RbacRolePermissionRepository;
import com.lingsql.rbac.repository.RbacRoleRepository;
import com.lingsql.rbac.repository.RbacTenantPermissionRepository;
import com.lingsql.rbac.repository.RbacUserRepository;
import com.lingsql.rbac.repository.RbacUserRoleRepository;
import com.lingsql.tenant.entity.TenantInfo;
import com.lingsql.tenant.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
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
    private static final String TENANT_ADMIN_ROLE_CODE = "TENANT_ADMIN";
    private static final int ACTIVE_STATUS = 1;
    private static final int NOT_DELETED = 0;
    private static final long SYSTEM_TENANT_BALANCE = Long.MAX_VALUE;
    private static final List<String> TENANT_ADMIN_ORG_PERMISSION_CODES = List.of(
            "MENU:ADMIN",
            "MENU:ADMIN:ORG",
            "ADMIN:ORG:READ",
            "ADMIN:ORG:WRITE",
            "ADMIN:ORG:DELETE"
    );
    private static final Set<String> FORBIDDEN_PASSWORDS = Set.of(
            "Admin123456",
            "admin123456",
            "admin",
            "12345678",
            "password"
    );

    private final LingSqlProperties properties;
    private final TenantRepository tenantRepository;
    private final RbacUserRepository userRepository;
    private final RbacRoleRepository roleRepository;
    private final RbacPermissionRepository permissionRepository;
    private final RbacRolePermissionRepository rolePermissionRepository;
    private final RbacUserRoleRepository userRoleRepository;
    private final RbacMenuRepository menuRepository;
    private final RbacTenantPermissionRepository tenantPermissionRepository;
    private final PasswordEncoder passwordEncoder;
    private final ObjectMapper objectMapper;
    private final ResourceLoader resourceLoader;
    private final BillingBalanceService billingBalanceService;
    private final BillingPlanRepository billingPlanRepository;

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
        if (isUnsafePassword(password, username)) {
            log.warn("超级管理员初始化失败: 禁止使用默认/弱口令");
            return;
        }
        RbacSeedData seedData = loadSeedData(admin.getSeedResource());
        ensurePermissions(seedData.permissions());
        ensureMenus(seedData.menus());
        repairTenantAdminOrgAccess();
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
        TenantInfo existing = tenantRepository.findByTenantIdAndIsDeleted(tenantId, NOT_DELETED).orElse(null);
        if (existing != null) {
            repairSystemTenant(existing);
            return;
        }
        TenantInfo tenant = new TenantInfo();
        tenant.setTenantId(tenantId);
        tenant.setTenantName(StringUtils.hasText(tenantName) ? tenantName.trim() : "系统租户");
        tenant.setStatus(ACTIVE_STATUS);
        tenant.setRemark("系统初始化");
        tenant.setPlanName("SYSTEM");
        tenant.setQuotaBalance(SYSTEM_TENANT_BALANCE);
        tenant.setServiceLevel(BillingPolicy.DEFAULT_SERVICE_LEVEL);
        tenant.setIsDeleted(NOT_DELETED);
        tenant.setCreateTime(LocalDateTime.now());
        tenant.setUpdateTime(LocalDateTime.now());
        try {
            TenantInfo saved = tenantRepository.save(tenant);
            billingBalanceService.resetBalance(saved.getTenantId(), saved.getQuotaBalance());
        } catch (DataIntegrityViolationException ex) {
            log.warn("租户初始化冲突: {}", ex.getMessage());
        }
    }

    /**
     * 修复历史系统租户的默认配额，避免超级管理员被付费墙阻断。
     *
     * @param tenant 系统租户
     * @author 余炘洋
     * @date 2026-04-10
     */
    private void repairSystemTenant(TenantInfo tenant) {
        if (tenant == null) {
            return;
        }
        boolean changed = false;
        if (tenant.getQuotaBalance() == null || tenant.getQuotaBalance() <= 0) {
            tenant.setQuotaBalance(resolveSystemTenantBalance(tenant.getPlanName()));
            changed = true;
        }
        if (!StringUtils.hasText(tenant.getPlanName())) {
            tenant.setPlanName("SYSTEM");
            changed = true;
        }
        if (!StringUtils.hasText(tenant.getServiceLevel())) {
            tenant.setServiceLevel(BillingPolicy.DEFAULT_SERVICE_LEVEL);
            changed = true;
        }
        if (!changed) {
            return;
        }
        tenant.setUpdateTime(LocalDateTime.now());
        TenantInfo saved = tenantRepository.save(tenant);
        billingBalanceService.resetBalance(saved.getTenantId(), saved.getQuotaBalance());
    }

    /**
     * 解析系统租户初始余额。
     * 若已绑定真实套餐，则优先回填套餐默认余额；否则退回系统无限余额。
     *
     * @param planName 套餐名称
     * @return 系统租户可用余额
     * @author 余炘洋
     * @date 2026-04-10
     */
    private long resolveSystemTenantBalance(String planName) {
        if (StringUtils.hasText(planName) && !"SYSTEM".equalsIgnoreCase(planName.trim())) {
            BillingPlan plan = billingPlanRepository.findByPlanNameAndIsDeleted(planName.trim(), NOT_DELETED).orElse(null);
            if (plan != null && plan.getDefaultQuotaBalance() != null && plan.getDefaultQuotaBalance() > 0) {
                return plan.getDefaultQuotaBalance();
            }
        }
        return SYSTEM_TENANT_BALANCE;
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

    /**
     * 为历史租户管理员补齐组织架构入口和接口权限。
     *
     * <p>原因：组织域最初通过 SQL 种子落地，后续超级管理员初始化切到 `rbac-seed.json` 后，
     * 该文件一度缺失组织菜单与 `ADMIN:ORG:*` 权限，导致老环境中的 `TENANT_ADMIN`
     * 角色与租户权限池不会自动补齐这组能力。</p>
     *
     * @author 余炘洋
     * @date 2026-04-13
     */
    private void repairTenantAdminOrgAccess() {
        List<RbacPermission> requiredPermissions = permissionRepository.findByPermCodeIn(TENANT_ADMIN_ORG_PERMISSION_CODES);
        if (CollectionUtils.isEmpty(requiredPermissions)) {
            return;
        }
        List<RbacRole> tenantAdminRoles = roleRepository.findAll().stream()
                .filter(role -> role != null
                        && role.getId() != null
                        && StringUtils.hasText(role.getTenantId())
                        && TENANT_ADMIN_ROLE_CODE.equalsIgnoreCase(role.getRoleCode()))
                .toList();
        if (tenantAdminRoles.isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        List<RbacRolePermission> missingRolePermissions = new ArrayList<>();
        List<RbacTenantPermission> missingTenantPermissions = new ArrayList<>();
        Set<String> repairedTenantIds = new HashSet<>();
        for (RbacRole tenantAdminRole : tenantAdminRoles) {
            Set<Long> existingRolePermissionIds = new HashSet<>(rolePermissionRepository.findPermIdsByRoleIds(List.of(tenantAdminRole.getId())));
            for (RbacPermission permission : requiredPermissions) {
                if (permission == null || permission.getId() == null || existingRolePermissionIds.contains(permission.getId())) {
                    continue;
                }
                RbacRolePermission relation = new RbacRolePermission();
                relation.setRoleId(tenantAdminRole.getId());
                relation.setPermId(permission.getId());
                relation.setCreateTime(now);
                missingRolePermissions.add(relation);
            }
            if (!repairedTenantIds.add(tenantAdminRole.getTenantId())) {
                continue;
            }
            Set<Long> existingTenantPermissionIds = new HashSet<>(tenantPermissionRepository.findPermIdsByTenantId(tenantAdminRole.getTenantId()));
            for (RbacPermission permission : requiredPermissions) {
                if (permission == null || permission.getId() == null || existingTenantPermissionIds.contains(permission.getId())) {
                    continue;
                }
                RbacTenantPermission relation = new RbacTenantPermission();
                relation.setTenantId(tenantAdminRole.getTenantId());
                relation.setPermId(permission.getId());
                relation.setCreateTime(now);
                missingTenantPermissions.add(relation);
            }
        }
        if (!missingRolePermissions.isEmpty()) {
            try {
                rolePermissionRepository.saveAll(missingRolePermissions);
            } catch (DataIntegrityViolationException ex) {
                log.warn("租户管理员组织权限补齐冲突: {}", ex.getMessage());
            }
        }
        if (!missingTenantPermissions.isEmpty()) {
            try {
                tenantPermissionRepository.saveAll(missingTenantPermissions);
            } catch (DataIntegrityViolationException ex) {
                log.warn("租户权限池组织权限补齐冲突: {}", ex.getMessage());
            }
        }
    }

    private void ensurePermissions(List<PermissionSeed> seeds) {
        if (CollectionUtils.isEmpty(seeds)) {
            log.warn("权限初始化跳过: 种子数据为空");
            return;
        }
        Set<String> existingCodes = permissionRepository.findAll().stream()
                .map(RbacPermission::getPermCode)
                .filter(StringUtils::hasText)
                .collect(Collectors.toSet());
        LocalDateTime now = LocalDateTime.now();
        List<RbacPermission> toSave = new ArrayList<>();
        for (PermissionSeed seed : seeds) {
            if (!StringUtils.hasText(seed.permCode)) {
                continue;
            }
            if (existingCodes.contains(seed.permCode)) {
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

    private void ensureMenus(List<MenuSeed> seeds) {
        if (CollectionUtils.isEmpty(seeds)) {
            log.warn("菜单初始化跳过: 种子数据为空");
            return;
        }
        List<RbacMenu> existingMenus = menuRepository.findByTenantIdIsNull();
        Map<String, RbacMenu> menuMap = existingMenus.stream()
                .filter(menu -> StringUtils.hasText(menu.getMenuCode()))
                .collect(Collectors.toMap(RbacMenu::getMenuCode, menu -> menu, (a, b) -> a, HashMap::new));
        LocalDateTime now = LocalDateTime.now();
        List<MenuSeed> pending = new ArrayList<>(seeds);
        int lastPendingSize = -1;
        while (!pending.isEmpty() && pending.size() != lastPendingSize) {
            lastPendingSize = pending.size();
            var iterator = pending.iterator();
            while (iterator.hasNext()) {
                MenuSeed seed = iterator.next();
                if (!StringUtils.hasText(seed.menuCode)) {
                    iterator.remove();
                    continue;
                }
                if (menuMap.containsKey(seed.menuCode)) {
                    iterator.remove();
                    continue;
                }
                Long parentId = null;
                if (StringUtils.hasText(seed.parentCode)) {
                    RbacMenu parent = menuMap.get(seed.parentCode);
                    if (parent == null) {
                        continue;
                    }
                    parentId = parent.getId();
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
                menu.setOrgTypeCodes(normalize(seed.orgTypeCodes));
                menu.setOrgUnitCodes(normalize(seed.orgUnitCodes));
                menu.setOrgTags(normalize(seed.orgTags));
                menu.setRequiredRelationTypes(normalize(seed.requiredRelationTypes));
                menu.setCreateTime(now);
                menu.setUpdateTime(now);
                try {
                    RbacMenu saved = menuRepository.save(menu);
                    menuMap.put(saved.getMenuCode(), saved);
                    iterator.remove();
                } catch (DataIntegrityViolationException ex) {
                    log.warn("菜单初始化冲突: {}", ex.getMessage());
                    iterator.remove();
                }
            }
        }
        if (!pending.isEmpty()) {
            String menuCodes = pending.stream()
                    .map(MenuSeed::menuCode)
                    .filter(StringUtils::hasText)
                    .collect(Collectors.joining(","));
            log.warn("菜单初始化跳过，父级缺失或配置异常: {}", menuCodes);
        }
    }

    private RbacSeedData loadSeedData(String resourceLocation) {
        if (!StringUtils.hasText(resourceLocation)) {
            throw new IllegalStateException("权限种子资源未配置");
        }
        Resource resource = resourceLoader.getResource(resourceLocation.trim());
        if (!resource.exists()) {
            throw new IllegalStateException("权限种子资源不存在: " + resourceLocation);
        }
        try (InputStream inputStream = resource.getInputStream()) {
            return objectMapper.readValue(inputStream, RbacSeedData.class);
        } catch (IOException ex) {
            throw new IllegalStateException("权限种子资源解析失败: " + resourceLocation, ex);
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

    private boolean isUnsafePassword(String password, String username) {
        if (!StringUtils.hasText(password)) {
            return true;
        }
        String trimmed = password.trim();
        if (FORBIDDEN_PASSWORDS.contains(trimmed)) {
            return true;
        }
        if (StringUtils.hasText(username) && trimmed.equalsIgnoreCase(username.trim())) {
            return true;
        }
        return false;
    }

    private String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record RbacSeedData(List<PermissionSeed> permissions, List<MenuSeed> menus) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record PermissionSeed(String permCode, String permName, String permType, Integer status) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record MenuSeed(String menuCode, String menuName, String parentCode, String path, String component,
                            String icon, Integer sort, Integer visible, Integer status, String permCode,
                            String orgTypeCodes, String orgUnitCodes, String orgTags, String requiredRelationTypes) {
    }
}
