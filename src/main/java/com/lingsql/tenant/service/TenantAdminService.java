package com.lingsql.tenant.service;

import com.lingsql.datasource.model.TenantDbConfig;
import com.lingsql.datasource.repository.TenantDbConfigRepository;
import com.lingsql.executor.SqlTaskTracker;
import com.lingsql.rbac.entity.RbacPermission;
import com.lingsql.rbac.entity.RbacRole;
import com.lingsql.rbac.entity.RbacRoleDataScope;
import com.lingsql.rbac.entity.RbacRolePermission;
import com.lingsql.rbac.entity.RbacTenantPermission;
import com.lingsql.rbac.model.RbacDataScopeType;
import com.lingsql.rbac.repository.RbacPermissionRepository;
import com.lingsql.rbac.repository.RbacRoleDataScopeRepository;
import com.lingsql.rbac.repository.RbacRolePermissionRepository;
import com.lingsql.rbac.repository.RbacRoleRepository;
import com.lingsql.rbac.repository.RbacTenantPermissionRepository;
import com.lingsql.rbac.repository.RbacUserRepository;
import com.lingsql.tenant.entity.TenantInfo;
import com.lingsql.tenant.model.TenantCreateRequest;
import com.lingsql.tenant.model.TenantUpdateRequest;
import com.lingsql.tenant.model.TenantView;
import com.lingsql.tenant.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 租户管理服务。
 *
 * @author 余炘洋
 * @date 2026-01-26
 */
@Service
@RequiredArgsConstructor
public class TenantAdminService {

    private static final Integer NOT_DELETED = 0;
    private static final Integer DELETED = 1;
    private static final Integer ACTIVE_STATUS = 1;
    private static final Integer INACTIVE_STATUS = 0;
    private static final String TENANT_ADMIN_ROLE_CODE = "TENANT_ADMIN";

    private static final List<String> BASE_PERMISSION_CODES = List.of(
            "MENU:CHAT",
            "MENU:CHAT:HISTORY",
            "API:CHAT:ASK",
            "API:CHAT:HISTORY:READ",
            "API:CHAT:HISTORY:WRITE",
            "CONFIG:CHAT:READ"
    );

    private final TenantRepository tenantRepository;
    private final TenantDbConfigRepository tenantDbConfigRepository;
    private final RbacPermissionRepository permissionRepository;
    private final RbacTenantPermissionRepository tenantPermissionRepository;
    private final RbacRoleRepository roleRepository;
    private final RbacRolePermissionRepository rolePermissionRepository;
    private final RbacRoleDataScopeRepository roleDataScopeRepository;
    private final RbacUserRepository userRepository;
    private final SqlTaskTracker sqlTaskTracker;

    /**
     * 查询租户列表。
     *
     * @return 租户列表
     * @author 余炘洋
     * @date 2026-01-26
     */
    public List<TenantView> listTenants() {
        return tenantRepository.findByIsDeleted(NOT_DELETED).stream()
                .map(this::toView)
                .toList();
    }

    /**
     * 创建租户。
     *
     * @param request 创建请求
     * @return 租户视图
     * @author 余炘洋
     * @date 2026-01-26
     */
    @Transactional
    public TenantView createTenant(TenantCreateRequest request) {
        if (request == null) {
            throw new RuntimeException("租户信息不能为空");
        }
        String tenantId = normalizeTenantId(request.getTenantId());
        if (!StringUtils.hasText(request.getTenantName())) {
            throw new RuntimeException("租户名称不能为空");
        }
        tenantId = resolveTenantIdForCreate(tenantId);
        TenantInfo tenant = new TenantInfo();
        tenant.setTenantId(tenantId);
        tenant.setTenantName(request.getTenantName().trim());
        tenant.setStatus(resolveStatus(request.getStatus()));
        tenant.setRemark(request.getRemark());
        tenant.setContactName(request.getContactName());
        tenant.setContactPhone(request.getContactPhone());
        tenant.setContactEmail(request.getContactEmail());
        tenant.setExpireTime(parseDateTime(request.getExpireTime()));
        tenant.setPlanName(request.getPlanName());
        tenant.setQuotaBalance(resolveQuotaBalance(request.getQuotaBalance()));
        tenant.setServiceLevel(resolveServiceLevel(request.getServiceLevel()));
        tenant.setTenantType(resolveTenantType(request.getTenantType()));
        tenant.setIsDeleted(NOT_DELETED);
        tenant.setCreateTime(LocalDateTime.now());
        tenant.setUpdateTime(LocalDateTime.now());
        TenantInfo saved = tenantRepository.save(tenant);
        initializeTenant(saved, request);
        return toView(saved);
    }

    /**
     * 创建租户时解析租户ID，支持系统自动生成。
     *
     * @param tenantId 输入租户ID
     * @return 可用租户ID
     * @author 余炘洋
     * @date 2026-01-26
     */
    private String resolveTenantIdForCreate(String tenantId) {
        if (StringUtils.hasText(tenantId)) {
            if (tenantRepository.existsByTenantIdAndIsDeleted(tenantId, NOT_DELETED)
                    || tenantRepository.existsByTenantIdAndIsDeleted(tenantId, DELETED)) {
                throw new RuntimeException("租户ID已存在");
            }
            return tenantId;
        }
        String generated = generateTenantId();
        int attempts = 0;
        while (tenantRepository.existsByTenantIdAndIsDeleted(generated, NOT_DELETED)
                || tenantRepository.existsByTenantIdAndIsDeleted(generated, DELETED)) {
            if (attempts++ >= 5) {
                throw new RuntimeException("租户ID生成失败，请重试");
            }
            generated = generateTenantId();
        }
        return generated;
    }

    /**
     * 生成租户ID。
     *
     * @return 租户ID
     * @author 余炘洋
     * @date 2026-01-26
     */
    private String generateTenantId() {
        String timestamp = DateTimeFormatter.ofPattern("yyyyMMddHHmmss", Locale.ROOT)
                .format(LocalDateTime.now());
        int random = ThreadLocalRandom.current().nextInt(1000, 10000);
        return "T" + timestamp + random;
    }

    /**
     * 更新租户。
     *
     * @param id      租户ID
     * @param request 更新请求
     * @return 租户视图
     * @author 余炘洋
     * @date 2026-01-26
     */
    @Transactional
    public TenantView updateTenant(Long id, TenantUpdateRequest request) {
        if (request == null) {
            throw new RuntimeException("租户信息不能为空");
        }
        TenantInfo tenant = tenantRepository.findByIdAndIsDeleted(id, NOT_DELETED)
                .orElseThrow(() -> new RuntimeException("租户不存在"));
        if (StringUtils.hasText(request.getTenantName())) {
            tenant.setTenantName(request.getTenantName().trim());
        }
        if (request.getStatus() != null) {
            tenant.setStatus(request.getStatus());
        }
        tenant.setRemark(request.getRemark());
        tenant.setContactName(request.getContactName());
        tenant.setContactPhone(request.getContactPhone());
        tenant.setContactEmail(request.getContactEmail());
        tenant.setExpireTime(parseDateTime(request.getExpireTime()));
        tenant.setPlanName(request.getPlanName());
        if (request.getQuotaBalance() != null) {
            tenant.setQuotaBalance(resolveQuotaBalance(request.getQuotaBalance()));
        }
        if (StringUtils.hasText(request.getServiceLevel())) {
            tenant.setServiceLevel(resolveServiceLevel(request.getServiceLevel()));
        }
        if (StringUtils.hasText(request.getTenantType())) {
            tenant.setTenantType(resolveTenantType(request.getTenantType()));
        }
        tenant.setUpdateTime(LocalDateTime.now());
        TenantInfo saved = tenantRepository.save(tenant);
        return toView(saved);
    }

    /**
     * 删除租户（软删除）。
     *
     * @param id 租户ID
     * @author 余炘洋
     * @date 2026-01-26
     */
    @Transactional
    public void deleteTenant(Long id) {
        TenantInfo tenant = tenantRepository.findByIdAndIsDeleted(id, NOT_DELETED)
                .orElseThrow(() -> new RuntimeException("租户不存在"));
        String tenantId = tenant.getTenantId();
        if (userRepository.countByTenantIdAndStatus(tenantId, ACTIVE_STATUS) > 0) {
            throw new RuntimeException("租户下存在活跃用户，无法删除");
        }
        if (sqlTaskTracker.hasRunning(tenantId)) {
            throw new RuntimeException("租户存在正在运行的 SQL 任务，无法删除");
        }
        tenant.setIsDeleted(DELETED);
        tenant.setStatus(INACTIVE_STATUS);
        tenant.setUpdateTime(LocalDateTime.now());
        tenantRepository.save(tenant);
        freezeTenantConfig(tenantId);
        tenantPermissionRepository.deleteByTenantId(tenantId);
    }

    /**
     * 初始化租户基础能力。
     *
     * @param tenant  租户实体
     * @param request 创建请求
     * @author 余炘洋
     * @date 2026-01-26
     */
    private void initializeTenant(TenantInfo tenant, TenantCreateRequest request) {
        RbacRole role = ensureTenantAdminRole(tenant.getTenantId());
        ensureTenantDataScope(role.getId());
        List<RbacPermission> basePermissions = resolveBasePermissions();
        initTenantPermissionPool(tenant.getTenantId(), basePermissions);
        initRolePermissions(role.getId(), basePermissions);
        initTenantDbConfig(tenant.getTenantId(), request);
    }

    /**
     * 确保租户管理员角色存在。
     *
     * @param tenantId 租户ID
     * @return 角色实体
     * @author 余炘洋
     * @date 2026-01-26
     */
    private RbacRole ensureTenantAdminRole(String tenantId) {
        Optional<RbacRole> existing = roleRepository.findByTenantIdAndRoleCode(tenantId, TENANT_ADMIN_ROLE_CODE);
        if (existing.isPresent()) {
            return existing.get();
        }
        RbacRole role = new RbacRole();
        role.setTenantId(tenantId);
        role.setRoleCode(TENANT_ADMIN_ROLE_CODE);
        role.setRoleName("租户管理员");
        role.setStatus(ACTIVE_STATUS);
        role.setCreateTime(LocalDateTime.now());
        role.setUpdateTime(LocalDateTime.now());
        return roleRepository.save(role);
    }

    /**
     * 确保租户管理员拥有租户范围数据权限。
     *
     * @param roleId 角色ID
     * @author 余炘洋
     * @date 2026-01-26
     */
    private void ensureTenantDataScope(Long roleId) {
        List<RbacRoleDataScope> scopes = roleDataScopeRepository.findActiveByRoleIds(List.of(roleId));
        if (scopes != null && !scopes.isEmpty()) {
            return;
        }
        RbacRoleDataScope scope = new RbacRoleDataScope();
        scope.setRoleId(roleId);
        scope.setScopeType(RbacDataScopeType.TENANT);
        scope.setScopeColumn(null);
        scope.setScopeRule(null);
        scope.setStatus(ACTIVE_STATUS);
        scope.setCreateTime(LocalDateTime.now());
        scope.setUpdateTime(LocalDateTime.now());
        roleDataScopeRepository.save(scope);
    }

    /**
     * 初始化租户权限池。
     *
     * @param tenantId    租户ID
     * @param permissions 权限列表
     * @author 余炘洋
     * @date 2026-01-26
     */
    private void initTenantPermissionPool(String tenantId, List<RbacPermission> permissions) {
        if (permissions == null || permissions.isEmpty()) {
            return;
        }
        List<Long> existing = tenantPermissionRepository.findPermIdsByTenantId(tenantId);
        List<RbacTenantPermission> entities = new ArrayList<>();
        for (RbacPermission permission : permissions) {
            if (existing != null && existing.contains(permission.getId())) {
                continue;
            }
            RbacTenantPermission relation = new RbacTenantPermission();
            relation.setTenantId(tenantId);
            relation.setPermId(permission.getId());
            relation.setCreateTime(LocalDateTime.now());
            entities.add(relation);
        }
        tenantPermissionRepository.saveAll(entities);
    }

    /**
     * 初始化角色权限关系。
     *
     * @param roleId      角色ID
     * @param permissions 权限列表
     * @author 余炘洋
     * @date 2026-01-26
     */
    private void initRolePermissions(Long roleId, List<RbacPermission> permissions) {
        if (permissions == null || permissions.isEmpty()) {
            return;
        }
        List<Long> existing = rolePermissionRepository.findPermIdsByRoleIds(List.of(roleId));
        List<RbacRolePermission> relations = new ArrayList<>();
        for (RbacPermission permission : permissions) {
            if (existing != null && existing.contains(permission.getId())) {
                continue;
            }
            RbacRolePermission relation = new RbacRolePermission();
            relation.setRoleId(roleId);
            relation.setPermId(permission.getId());
            relation.setCreateTime(LocalDateTime.now());
            relations.add(relation);
        }
        rolePermissionRepository.saveAll(relations);
    }

    /**
     * 初始化租户数据源配置。
     *
     * @param tenantId 租户ID
     * @param request  创建请求
     * @author 余炘洋
     * @date 2026-01-26
     */
    private void initTenantDbConfig(String tenantId, TenantCreateRequest request) {
        if (request == null) {
            return;
        }
        String isolationMode = normalizeText(request.getIsolationMode());
        String jdbcUrl = normalizeText(request.getJdbcUrl());
        if (!StringUtils.hasText(jdbcUrl) && !"LOGICAL".equalsIgnoreCase(isolationMode)) {
            return;
        }
        if ("LOGICAL".equalsIgnoreCase(isolationMode) && !StringUtils.hasText(jdbcUrl)) {
            throw new RuntimeException("逻辑隔离模式需配置 JDBC 地址");
        }
        if (tenantDbConfigRepository.findByTenantId(tenantId).isPresent()) {
            return;
        }
        TenantDbConfig config = new TenantDbConfig();
        config.setTenantId(tenantId);
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(request.getUsername());
        config.setPassword(request.getPassword());
        config.setDriverClass(StringUtils.hasText(request.getDriverClass()) ? request.getDriverClass() : "com.mysql.cj.jdbc.Driver");
        config.setIsolationMode(StringUtils.hasText(isolationMode) ? isolationMode : "PHYSICAL");
        config.setLogicColumn(StringUtils.hasText(request.getLogicColumn()) ? request.getLogicColumn() : "tenant_id");
        config.setStatus(ACTIVE_STATUS);
        tenantDbConfigRepository.save(config);
    }

    /**
     * 冻结租户数据源配置。
     *
     * @param tenantId 租户ID
     * @author 余炘洋
     * @date 2026-01-26
     */
    private void freezeTenantConfig(String tenantId) {
        tenantDbConfigRepository.findByTenantId(tenantId).ifPresent(config -> {
            config.setStatus(INACTIVE_STATUS);
            tenantDbConfigRepository.save(config);
        });
    }

    /**
     * 获取基础权限集合。
     *
     * @return 权限列表
     * @author 余炘洋
     * @date 2026-01-26
     */
    private List<RbacPermission> resolveBasePermissions() {
        List<RbacPermission> permissions = permissionRepository.findByPermCodeIn(BASE_PERMISSION_CODES);
        if (permissions != null && !permissions.isEmpty()) {
            return permissions;
        }
        return permissionRepository.findAll().stream()
                .filter(item -> item.getPermCode() != null)
                .filter(item -> !item.getPermCode().startsWith("MENU:ADMIN"))
                .filter(item -> !item.getPermCode().startsWith("ADMIN:"))
                .toList();
    }

    /**
     * 转换为视图对象。
     *
     * @param tenant 租户实体
     * @return 租户视图
     * @author 余炘洋
     * @date 2026-01-26
     */
    private TenantView toView(TenantInfo tenant) {
        TenantView view = new TenantView();
        view.setId(tenant.getId());
        view.setTenantId(tenant.getTenantId());
        view.setTenantName(tenant.getTenantName());
        view.setStatus(tenant.getStatus());
        view.setRemark(tenant.getRemark());
        view.setContactName(tenant.getContactName());
        view.setContactPhone(tenant.getContactPhone());
        view.setContactEmail(tenant.getContactEmail());
        view.setExpireTime(tenant.getExpireTime());
        view.setPlanName(tenant.getPlanName());
        view.setQuotaBalance(tenant.getQuotaBalance());
        view.setServiceLevel(tenant.getServiceLevel());
        view.setTenantType(tenant.getTenantType());
        view.setCreateTime(tenant.getCreateTime());
        view.setUpdateTime(tenant.getUpdateTime());
        return view;
    }

    /**
     * 解析余额。
     *
     * @param quotaBalance 余额
     * @return 规范余额
     * @author 余炘洋
     * @date 2026-01-27
     */
    private Long resolveQuotaBalance(Long quotaBalance) {
        if (quotaBalance == null || quotaBalance < 0) {
            return 0L;
        }
        return quotaBalance;
    }

    /**
     * 解析服务等级。
     *
     * @param serviceLevel 服务等级
     * @return 规范等级
     * @author 余炘洋
     * @date 2026-01-27
     */
    private String resolveServiceLevel(String serviceLevel) {
        if (!StringUtils.hasText(serviceLevel)) {
            return "FREE";
        }
        return serviceLevel.trim().toUpperCase();
    }

    /**
     * 解析租户类型。
     *
     * @param tenantType 租户类型
     * @return 规范类型
     * @author 余炘洋
     * @date 2026-01-27
     */
    private String resolveTenantType(String tenantType) {
        if (!StringUtils.hasText(tenantType)) {
            return null;
        }
        return tenantType.trim().toUpperCase();
    }

    /**
     * 解析时间字符串。
     *
     * @param value 时间字符串
     * @return 时间对象
     * @author 余炘洋
     * @date 2026-01-26
     */
    private LocalDateTime parseDateTime(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String text = value.trim();
        try {
            return LocalDateTime.parse(text, DateTimeFormatter.ISO_DATE_TIME);
        } catch (DateTimeParseException ignored) {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.SIMPLIFIED_CHINESE);
            return LocalDateTime.parse(text, formatter);
        }
    }

    /**
     * 规范化租户ID。
     *
     * @param tenantId 租户ID
     * @return 规范化租户ID
     * @author 余炘洋
     * @date 2026-01-26
     */
    private String normalizeTenantId(String tenantId) {
        if (!StringUtils.hasText(tenantId)) {
            return null;
        }
        return tenantId.trim();
    }

    /**
     * 规范化文本。
     *
     * @param value 文本
     * @return 规范化值
     * @author 余炘洋
     * @date 2026-01-26
     */
    private String normalizeText(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    /**
     * 解析状态。
     *
     * @param status 状态值
     * @return 状态
     * @author 余炘洋
     * @date 2026-01-26
     */
    private Integer resolveStatus(Integer status) {
        return status == null ? ACTIVE_STATUS : status;
    }
}
