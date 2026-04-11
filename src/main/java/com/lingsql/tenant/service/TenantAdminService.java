package com.lingsql.tenant.service;

import com.lingsql.billing.entity.BillingPlan;
import com.lingsql.billing.repository.BillingPlanRepository;
import com.lingsql.common.exception.BizException;
import com.lingsql.common.exception.ErrorCode;
import com.lingsql.datasource.model.TenantDbConfig;
import com.lingsql.datasource.repository.TenantDbConfigRepository;
import com.lingsql.executor.SqlTaskTracker;
import com.lingsql.billing.BillingBalanceService;
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
import com.lingsql.tenant.model.TenantLifecycleExtendRequest;
import com.lingsql.tenant.model.TenantLifecycleOpsTaskView;
import com.lingsql.tenant.model.TenantLifecycleSnapshot;
import com.lingsql.tenant.model.TenantLifecycleState;
import com.lingsql.tenant.model.TenantLifecycleSummaryView;
import com.lingsql.tenant.model.TenantLifecycleWorkbenchView;
import com.lingsql.tenant.model.TenantRiskLevel;
import com.lingsql.tenant.model.TenantRiskSnapshot;
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
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
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
    private static final int DEFAULT_WORKBENCH_LIMIT = 50;
    private static final int MAX_WORKBENCH_LIMIT = 200;
    private static final String PRIORITY_P0 = "P0";
    private static final String PRIORITY_P1 = "P1";
    private static final String PRIORITY_P2 = "P2";

    private static final List<String> BASE_PERMISSION_CODES = List.of(
            "MENU:ADMIN",
            "MENU:ADMIN:ORG",
            "ADMIN:ORG:READ",
            "ADMIN:ORG:WRITE",
            "ADMIN:ORG:DELETE",
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
    private final BillingBalanceService billingBalanceService;
    private final BillingPlanRepository billingPlanRepository;
    private final TenantLifecycleService tenantLifecycleService;

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
     * 查询租户生命周期汇总。
     *
     * @return 生命周期汇总
     * @author 余炘洋
     * @date 2026-02-27
     */
    public TenantLifecycleSummaryView lifecycleSummary() {
        List<TenantInfo> tenants = tenantRepository.findByIsDeleted(NOT_DELETED);
        TenantLifecycleSummaryView summary = new TenantLifecycleSummaryView();
        summary.setTotal(tenants.size());
        LocalDateTime now = LocalDateTime.now();
        for (TenantInfo tenant : tenants) {
            TenantLifecycleSnapshot snapshot = tenantLifecycleService.evaluate(tenant, now);
            if (snapshot.state() == TenantLifecycleState.ACTIVE) {
                summary.setActive(summary.getActive() + 1);
            } else if (snapshot.state() == TenantLifecycleState.EXPIRING_SOON) {
                summary.setExpiringSoon(summary.getExpiringSoon() + 1);
            } else if (snapshot.state() == TenantLifecycleState.GRACE_PERIOD) {
                summary.setGracePeriod(summary.getGracePeriod() + 1);
            } else if (snapshot.state() == TenantLifecycleState.SUSPENDED) {
                summary.setSuspended(summary.getSuspended() + 1);
            } else {
                summary.setDisabled(summary.getDisabled() + 1);
            }
            TenantRiskSnapshot riskSnapshot = tenantLifecycleService.evaluateRisk(tenant, now);
            if (riskSnapshot == null) {
                continue;
            }
            if (riskSnapshot.level() == TenantRiskLevel.MEDIUM
                    || riskSnapshot.level() == TenantRiskLevel.HIGH
                    || riskSnapshot.level() == TenantRiskLevel.CRITICAL) {
                summary.setAtRisk(summary.getAtRisk() + 1);
            }
            if (riskSnapshot.level() == TenantRiskLevel.HIGH) {
                summary.setHighRisk(summary.getHighRisk() + 1);
            }
            if (riskSnapshot.level() == TenantRiskLevel.CRITICAL) {
                summary.setCriticalRisk(summary.getCriticalRisk() + 1);
            }
        }
        return summary;
    }

    /**
     * 查询租户生命周期运营工作台。
     *
     * @param limit 返回任务条数上限
     * @return 运营工作台
     * @author 余炘洋
     * @date 2026-02-27
     */
    public TenantLifecycleWorkbenchView lifecycleWorkbench(Integer limit) {
        int resolvedLimit = resolveWorkbenchLimit(limit);
        LocalDateTime now = LocalDateTime.now();
        List<TenantLifecycleOpsTaskView> tasks = new ArrayList<>();
        for (TenantInfo tenant : tenantRepository.findByIsDeleted(NOT_DELETED)) {
            TenantLifecycleSnapshot snapshot = tenantLifecycleService.evaluate(tenant, now);
            TenantRiskSnapshot riskSnapshot = tenantLifecycleService.evaluateRisk(tenant, now);
            TenantLifecycleOpsTaskView task = buildOpsTask(tenant, snapshot, riskSnapshot);
            if (task != null) {
                tasks.add(task);
            }
        }
        tasks.sort(
                Comparator.comparingInt((TenantLifecycleOpsTaskView item) -> priorityWeight(item.getPriority())).reversed()
                        .thenComparing((TenantLifecycleOpsTaskView item) -> item.getRiskScore() == null ? 0 : item.getRiskScore(), Comparator.reverseOrder())
                        .thenComparingLong(item -> item.getDaysToExpire() == null ? Long.MAX_VALUE : item.getDaysToExpire())
        );
        List<TenantLifecycleOpsTaskView> limitedTasks = tasks.stream()
                .limit(resolvedLimit)
                .toList();
        TenantLifecycleWorkbenchView workbench = new TenantLifecycleWorkbenchView();
        workbench.setGeneratedAt(now);
        workbench.setTotalTasks(limitedTasks.size());
        workbench.setP0Count((int) limitedTasks.stream().filter(task -> PRIORITY_P0.equals(task.getPriority())).count());
        workbench.setP1Count((int) limitedTasks.stream().filter(task -> PRIORITY_P1.equals(task.getPriority())).count());
        workbench.setP2Count((int) limitedTasks.stream().filter(task -> PRIORITY_P2.equals(task.getPriority())).count());
        workbench.setTasks(limitedTasks);
        return workbench;
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
            throw new BizException(ErrorCode.INVALID_PARAM, "租户信息不能为空");
        }
        String tenantId = normalizeTenantId(request.getTenantId());
        if (!StringUtils.hasText(request.getTenantName())) {
            throw new BizException(ErrorCode.INVALID_PARAM, "租户名称不能为空");
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
        String planName = normalizePlanName(request.getPlanName());
        BillingPlan plan = resolveBillingPlan(planName);
        tenant.setPlanName(planName);
        tenant.setQuotaBalance(resolveQuotaBalance(resolveQuotaBalanceForCreate(request.getQuotaBalance(), plan)));
        tenant.setServiceLevel(resolveServiceLevel(resolveServiceLevelForTenant(request.getServiceLevel(), plan)));
        tenant.setTenantType(resolveTenantType(resolveTenantTypeForTenant(request.getTenantType(), plan)));
        tenantLifecycleService.applyDefaultExpireTimeForTrial(tenant);
        tenant.setIsDeleted(NOT_DELETED);
        tenant.setCreateTime(LocalDateTime.now());
        tenant.setUpdateTime(LocalDateTime.now());
        TenantInfo saved = tenantRepository.save(tenant);
        initializeTenant(saved, request);
        billingBalanceService.resetBalance(saved.getTenantId(), saved.getQuotaBalance());
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
                throw new BizException(ErrorCode.CONFLICT, "租户ID已存在");
            }
            return tenantId;
        }
        String generated = generateTenantId();
        int attempts = 0;
        while (tenantRepository.existsByTenantIdAndIsDeleted(generated, NOT_DELETED)
                || tenantRepository.existsByTenantIdAndIsDeleted(generated, DELETED)) {
            if (attempts++ >= 5) {
                throw new BizException(ErrorCode.BUSINESS_ERROR, "租户ID生成失败，请重试");
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
            throw new BizException(ErrorCode.INVALID_PARAM, "租户信息不能为空");
        }
        TenantInfo tenant = tenantRepository.findByIdAndIsDeleted(id, NOT_DELETED)
                .orElseThrow(() -> new BizException(ErrorCode.TENANT_NOT_FOUND, "租户不存在"));
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
        String previousPlanName = normalizePlanName(tenant.getPlanName());
        String targetPlanName = request.getPlanName() == null ? previousPlanName : normalizePlanName(request.getPlanName());
        BillingPlan plan = resolveBillingPlan(targetPlanName);
        boolean planChanged = !Objects.equals(previousPlanName, targetPlanName);
        tenant.setPlanName(targetPlanName);
        Long resolvedQuotaBalance = resolveQuotaBalanceForUpdate(request.getQuotaBalance(), tenant.getQuotaBalance(), plan, planChanged);
        if (resolvedQuotaBalance != null) {
            tenant.setQuotaBalance(resolveQuotaBalance(resolvedQuotaBalance));
        }
        tenant.setServiceLevel(resolveServiceLevel(resolveServiceLevelForUpdate(request.getServiceLevel(), tenant.getServiceLevel(), plan)));
        tenant.setTenantType(resolveTenantType(resolveTenantTypeForUpdate(request.getTenantType(), tenant.getTenantType(), plan)));
        tenant.setUpdateTime(LocalDateTime.now());
        TenantInfo saved = tenantRepository.save(tenant);
        if (request.getQuotaBalance() != null || resolvedQuotaBalance != null) {
            billingBalanceService.resetBalance(saved.getTenantId(), saved.getQuotaBalance());
        }
        return toView(saved);
    }

    /**
     * 续期租户生命周期。
     *
     * @param id      租户ID
     * @param request 续期请求
     * @return 租户视图
     * @author 余炘洋
     * @date 2026-02-27
     */
    @Transactional
    public TenantView extendTenantLifecycle(Long id, TenantLifecycleExtendRequest request) {
        TenantInfo tenant = tenantRepository.findByIdAndIsDeleted(id, NOT_DELETED)
                .orElseThrow(() -> new BizException(ErrorCode.TENANT_NOT_FOUND, "租户不存在"));
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime targetExpire = null;
        if (request != null) {
            targetExpire = parseDateTime(request.getExpireTime());
        }
        if (targetExpire == null) {
            int extendDays = resolveExtendDays(request);
            LocalDateTime base = tenant.getExpireTime();
            if (base == null || base.isBefore(now)) {
                base = now;
            }
            targetExpire = base.plusDays(extendDays);
        }
        if (targetExpire.isBefore(now)) {
            throw new BizException(ErrorCode.INVALID_PARAM, "到期时间不能早于当前时间");
        }
        tenant.setExpireTime(targetExpire);
        if (resolveActivate(request)) {
            tenant.setStatus(ACTIVE_STATUS);
            activateTenantConfig(tenant.getTenantId());
        }
        tenant.setUpdateTime(now);
        TenantInfo saved = tenantRepository.save(tenant);
        return toView(saved);
    }

    /**
     * 恢复租户服务。
     * 若租户已过期，则默认续期 30 天后再恢复。
     *
     * @param id 租户ID
     * @return 租户视图
     * @author 余炘洋
     * @date 2026-02-27
     */
    @Transactional
    public TenantView reactivateTenant(Long id) {
        TenantInfo tenant = tenantRepository.findByIdAndIsDeleted(id, NOT_DELETED)
                .orElseThrow(() -> new BizException(ErrorCode.TENANT_NOT_FOUND, "租户不存在"));
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expireTime = tenant.getExpireTime();
        if (expireTime == null || expireTime.isBefore(now)) {
            tenant.setExpireTime(now.plusDays(30));
        }
        tenant.setStatus(ACTIVE_STATUS);
        tenant.setUpdateTime(now);
        activateTenantConfig(tenant.getTenantId());
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
                .orElseThrow(() -> new BizException(ErrorCode.TENANT_NOT_FOUND, "租户不存在"));
        String tenantId = tenant.getTenantId();
        if (userRepository.countByTenantIdAndStatus(tenantId, ACTIVE_STATUS) > 0) {
            throw new BizException(ErrorCode.CONFLICT, "租户下存在活跃用户，无法删除");
        }
        if (sqlTaskTracker.hasRunning(tenantId)) {
            throw new BizException(ErrorCode.CONFLICT, "租户存在正在运行的 SQL 任务，无法删除");
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
            throw new BizException(ErrorCode.CONFIG_INVALID, "逻辑隔离模式需配置 JDBC 地址");
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
     * 恢复租户数据源配置。
     *
     * @param tenantId 租户ID
     * @author 余炘洋
     * @date 2026-02-27
     */
    private void activateTenantConfig(String tenantId) {
        tenantDbConfigRepository.findByTenantId(tenantId).ifPresent(config -> {
            config.setStatus(ACTIVE_STATUS);
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
        TenantLifecycleSnapshot snapshot = tenantLifecycleService.evaluate(tenant);
        view.setLifecycleState(snapshot.state().name());
        if (snapshot.daysToExpire() == Long.MAX_VALUE) {
            view.setDaysToExpire(null);
        } else {
            view.setDaysToExpire(snapshot.daysToExpire());
        }
        view.setGraceDaysRemaining(snapshot.graceDaysRemaining());
        TenantRiskSnapshot riskSnapshot = tenantLifecycleService.evaluateRisk(tenant);
        if (riskSnapshot == null) {
            view.setRiskScore(0);
            view.setRiskLevel(TenantRiskLevel.LOW.name());
            view.setRiskReasons(List.of());
        } else {
            view.setRiskScore(riskSnapshot.score());
            view.setRiskLevel(riskSnapshot.level().name());
            view.setRiskReasons(riskSnapshot.reasons());
        }
        return view;
    }

    private TenantLifecycleOpsTaskView buildOpsTask(TenantInfo tenant,
                                                    TenantLifecycleSnapshot lifecycleSnapshot,
                                                    TenantRiskSnapshot riskSnapshot) {
        if (tenant == null || lifecycleSnapshot == null || riskSnapshot == null) {
            return null;
        }
        String priority;
        String suggestedAction;
        int slaHours;
        if (lifecycleSnapshot.state() == TenantLifecycleState.SUSPENDED
                || riskSnapshot.level() == TenantRiskLevel.CRITICAL) {
            priority = PRIORITY_P0;
            suggestedAction = "立即联系客户并确认续期，必要时执行恢复服务";
            slaHours = 4;
        } else if (lifecycleSnapshot.state() == TenantLifecycleState.GRACE_PERIOD
                || lifecycleSnapshot.state() == TenantLifecycleState.DISABLED
                || riskSnapshot.level() == TenantRiskLevel.HIGH
                || (lifecycleSnapshot.daysToExpire() != Long.MAX_VALUE && lifecycleSnapshot.daysToExpire() <= 1)) {
            priority = PRIORITY_P1;
            suggestedAction = "今日完成续期沟通并确认服务恢复计划";
            slaHours = 24;
        } else if (lifecycleSnapshot.state() == TenantLifecycleState.EXPIRING_SOON
                || riskSnapshot.level() == TenantRiskLevel.MEDIUM
                || (lifecycleSnapshot.daysToExpire() != Long.MAX_VALUE && lifecycleSnapshot.daysToExpire() <= 7)) {
            priority = PRIORITY_P2;
            suggestedAction = "发送续期提醒并安排客户回访";
            slaHours = 48;
        } else {
            return null;
        }
        TenantLifecycleOpsTaskView task = new TenantLifecycleOpsTaskView();
        task.setTenantId(tenant.getTenantId());
        task.setTenantName(tenant.getTenantName());
        task.setLifecycleState(lifecycleSnapshot.state().name());
        task.setRiskLevel(riskSnapshot.level().name());
        task.setRiskScore(riskSnapshot.score());
        task.setPriority(priority);
        task.setSuggestedAction(suggestedAction);
        task.setSlaHours(slaHours);
        if (lifecycleSnapshot.daysToExpire() != Long.MAX_VALUE) {
            task.setDaysToExpire(lifecycleSnapshot.daysToExpire());
        } else {
            task.setDaysToExpire(null);
        }
        task.setTriggerReason(resolveTriggerReason(lifecycleSnapshot, riskSnapshot));
        task.setContactName(tenant.getContactName());
        task.setContactPhone(tenant.getContactPhone());
        task.setContactEmail(tenant.getContactEmail());
        return task;
    }

    private String resolveTriggerReason(TenantLifecycleSnapshot lifecycleSnapshot, TenantRiskSnapshot riskSnapshot) {
        List<String> reasons = new ArrayList<>();
        if (lifecycleSnapshot.state() == TenantLifecycleState.SUSPENDED) {
            reasons.add("租户已超宽限期并被冻结");
        } else if (lifecycleSnapshot.state() == TenantLifecycleState.GRACE_PERIOD) {
            reasons.add("租户处于宽限期");
        } else if (lifecycleSnapshot.state() == TenantLifecycleState.EXPIRING_SOON) {
            reasons.add("租户进入即将到期阶段");
        } else if (lifecycleSnapshot.state() == TenantLifecycleState.DISABLED) {
            reasons.add("租户当前处于停用状态");
        }
        Long daysToExpire = lifecycleSnapshot.daysToExpire() == Long.MAX_VALUE ? null : lifecycleSnapshot.daysToExpire();
        if (daysToExpire != null) {
            if (daysToExpire < 0) {
                reasons.add("已逾期 " + Math.abs(daysToExpire) + " 天");
            } else if (daysToExpire <= 3) {
                reasons.add("距离到期不足 3 天");
            } else if (daysToExpire <= 7) {
                reasons.add("距离到期不足 7 天");
            }
        }
        if (riskSnapshot.reasons() != null && !riskSnapshot.reasons().isEmpty()) {
            reasons.add(riskSnapshot.reasons().get(0));
        }
        if (reasons.isEmpty()) {
            reasons.add("建议执行常规客户回访");
        }
        return String.join("；", reasons);
    }

    private int resolveWorkbenchLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_WORKBENCH_LIMIT;
        }
        return Math.min(limit, MAX_WORKBENCH_LIMIT);
    }

    private int priorityWeight(String priority) {
        if (PRIORITY_P0.equalsIgnoreCase(priority)) {
            return 3;
        }
        if (PRIORITY_P1.equalsIgnoreCase(priority)) {
            return 2;
        }
        return 1;
    }

    /**
     * 规范化套餐名称。
     *
     * @param planName 套餐名称
     * @return 规范化结果
     * @author 余炘洋
     * @date 2026-04-10
     */
    private String normalizePlanName(String planName) {
        return StringUtils.hasText(planName) ? planName.trim() : null;
    }

    /**
     * 解析套餐配置。
     *
     * @param planName 套餐名称
     * @return 套餐配置
     * @author 余炘洋
     * @date 2026-04-10
     */
    private BillingPlan resolveBillingPlan(String planName) {
        if (!StringUtils.hasText(planName)) {
            return null;
        }
        return billingPlanRepository.findByPlanNameAndIsDeleted(planName, NOT_DELETED)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "套餐不存在"));
    }

    /**
     * 创建租户时解析余额。
     *
     * @param quotaBalance 显式余额
     * @param plan         套餐配置
     * @return 最终余额
     * @author 余炘洋
     * @date 2026-04-10
     */
    private Long resolveQuotaBalanceForCreate(Long quotaBalance, BillingPlan plan) {
        if (quotaBalance != null) {
            return quotaBalance;
        }
        if (plan != null) {
            return plan.getDefaultQuotaBalance();
        }
        return null;
    }

    /**
     * 更新租户时解析余额。
     *
     * @param requestQuotaBalance 显式余额
     * @param currentQuotaBalance 当前余额
     * @param plan                套餐配置
     * @param planChanged         是否切换套餐
     * @return 最终余额，返回 null 表示保持原值
     * @author 余炘洋
     * @date 2026-04-10
     */
    private Long resolveQuotaBalanceForUpdate(Long requestQuotaBalance,
                                              Long currentQuotaBalance,
                                              BillingPlan plan,
                                              boolean planChanged) {
        if (requestQuotaBalance != null) {
            return requestQuotaBalance;
        }
        if (plan == null) {
            return null;
        }
        if (planChanged || currentQuotaBalance == null || currentQuotaBalance <= 0) {
            return plan.getDefaultQuotaBalance();
        }
        return null;
    }

    /**
     * 创建租户时解析服务等级。
     *
     * @param requestServiceLevel 显式服务等级
     * @param plan                套餐配置
     * @return 最终服务等级
     * @author 余炘洋
     * @date 2026-04-10
     */
    private String resolveServiceLevelForTenant(String requestServiceLevel, BillingPlan plan) {
        if (StringUtils.hasText(requestServiceLevel)) {
            return requestServiceLevel;
        }
        if (plan != null) {
            return plan.getServiceLevel();
        }
        return null;
    }

    /**
     * 更新租户时解析服务等级。
     *
     * @param requestServiceLevel 显式服务等级
     * @param currentServiceLevel 当前服务等级
     * @param plan                套餐配置
     * @return 最终服务等级
     * @author 余炘洋
     * @date 2026-04-10
     */
    private String resolveServiceLevelForUpdate(String requestServiceLevel,
                                                String currentServiceLevel,
                                                BillingPlan plan) {
        if (StringUtils.hasText(requestServiceLevel)) {
            return requestServiceLevel;
        }
        if (plan != null) {
            return plan.getServiceLevel();
        }
        return currentServiceLevel;
    }

    /**
     * 创建租户时解析租户类型。
     *
     * @param requestTenantType 显式租户类型
     * @param plan              套餐配置
     * @return 最终租户类型
     * @author 余炘洋
     * @date 2026-04-10
     */
    private String resolveTenantTypeForTenant(String requestTenantType, BillingPlan plan) {
        if (StringUtils.hasText(requestTenantType)) {
            return requestTenantType;
        }
        if (plan != null) {
            return plan.getTenantType();
        }
        return null;
    }

    /**
     * 更新租户时解析租户类型。
     *
     * @param requestTenantType 显式租户类型
     * @param currentTenantType 当前租户类型
     * @param plan              套餐配置
     * @return 最终租户类型
     * @author 余炘洋
     * @date 2026-04-10
     */
    private String resolveTenantTypeForUpdate(String requestTenantType,
                                              String currentTenantType,
                                              BillingPlan plan) {
        if (StringUtils.hasText(requestTenantType)) {
            return requestTenantType;
        }
        if (plan != null) {
            return plan.getTenantType();
        }
        return currentTenantType;
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

    private int resolveExtendDays(TenantLifecycleExtendRequest request) {
        if (request == null || request.getExtendDays() == null) {
            return 30;
        }
        return Math.max(request.getExtendDays(), 1);
    }

    private boolean resolveActivate(TenantLifecycleExtendRequest request) {
        if (request == null || request.getActivate() == null) {
            return true;
        }
        return request.getActivate();
    }
}
