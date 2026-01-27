package com.lingsql.rbac.init;

import com.lingsql.billing.BillingPolicy;
import com.lingsql.config.LingSqlProperties;
import com.lingsql.rbac.entity.RbacPermission;
import com.lingsql.rbac.entity.RbacRole;
import com.lingsql.rbac.entity.RbacRolePermission;
import com.lingsql.rbac.entity.RbacUser;
import com.lingsql.rbac.entity.RbacUserRole;
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

    private final LingSqlProperties properties;
    private final TenantRepository tenantRepository;
    private final RbacUserRepository userRepository;
    private final RbacRoleRepository roleRepository;
    private final RbacPermissionRepository permissionRepository;
    private final RbacRolePermissionRepository rolePermissionRepository;
    private final RbacUserRoleRepository userRoleRepository;
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
}
