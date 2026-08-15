package com.lingsql.tenant.service;

import com.lingsql.billing.entity.BillingPlan;
import com.lingsql.billing.repository.BillingPlanRepository;
import com.lingsql.common.exception.BizException;
import com.lingsql.common.exception.ErrorCode;
import com.lingsql.datasource.model.TenantDbConfig;
import com.lingsql.datasource.repository.TenantDbConfigRepository;
import com.lingsql.datasource.TenantDataSourceConfigService;
import com.lingsql.executor.SqlTaskTracker;
import com.lingsql.billing.BillingBalanceService;
import com.lingsql.ops.OpsAlertService;
import com.lingsql.ops.model.AlertDispatchChannelResult;
import com.lingsql.ops.model.AlertDispatchResult;
import com.lingsql.organization.model.OrgTemplateApplyRequest;
import com.lingsql.organization.service.OrganizationAdminService;
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
import com.lingsql.rbac.repository.RbacUserRoleRepository;
import com.lingsql.rbac.model.UserCreateRequest;
import com.lingsql.rbac.service.UserAdminService;
import com.lingsql.tenant.entity.TenantInfo;
import com.lingsql.tenant.model.TenantCreateRequest;
import com.lingsql.tenant.model.TenantAdminAccountRequest;
import com.lingsql.tenant.model.TenantDataSourceProvisionRequest;
import com.lingsql.tenant.model.TenantLifecycleExtendRequest;
import com.lingsql.tenant.model.TenantLifecycleOpsTaskView;
import com.lingsql.tenant.model.TenantLifecycleSnapshot;
import com.lingsql.tenant.model.TenantLifecycleState;
import com.lingsql.tenant.model.TenantNotificationChannelHealthView;
import com.lingsql.tenant.model.TenantNotificationChannelIssueView;
import com.lingsql.tenant.model.TenantLifecycleSummaryView;
import com.lingsql.tenant.model.TenantLifecycleWorkbenchView;
import com.lingsql.tenant.model.TenantNotificationChannelTestChannelView;
import com.lingsql.tenant.model.TenantNotificationChannelTestRequest;
import com.lingsql.tenant.model.TenantNotificationChannelTestView;
import com.lingsql.tenant.model.TenantRiskLevel;
import com.lingsql.tenant.model.TenantRiskSnapshot;
import com.lingsql.tenant.model.TenantProvisioningReceipt;
import com.lingsql.tenant.model.TenantUpdateRequest;
import com.lingsql.tenant.model.TenantView;
import com.lingsql.tenant.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
    private static final String READY_STATUS = "READY";
    private static final String MYSQL_DRIVER_CLASS = "com.mysql.cj.jdbc.Driver";
    private static final String PHYSICAL_ISOLATION = "PHYSICAL";
    private static final String LOGICAL_ISOLATION = "LOGICAL";
    private static final int DEFAULT_WORKBENCH_LIMIT = 50;
    private static final int MAX_WORKBENCH_LIMIT = 200;
    private static final String PRIORITY_P0 = "P0";
    private static final String PRIORITY_P1 = "P1";
    private static final String PRIORITY_P2 = "P2";
    private static final List<String> SUPPORTED_NOTIFICATION_CHANNELS = List.of("webhook", "dingtalk", "wecom");

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
    private final OrganizationAdminService organizationAdminService;
    private final OpsAlertService opsAlertService;
    private final UserAdminService userAdminService;
    private final RbacUserRoleRepository userRoleRepository;
    private final TenantDataSourceConfigService tenantDataSourceConfigService;

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
     * 查询租户默认通知通道健康情况。
     *
     * @return 健康视图
     * @author 余炘洋
     * @date 2026-04-15
     */
    public TenantNotificationChannelHealthView notificationChannelHealth() {
        boolean alertEnabled = opsAlertService.isAlertEnabled();
        List<String> configuredChannels = opsAlertService.getConfiguredChannels();
        List<String> unconfiguredChannels = opsAlertService.getUnconfiguredChannels();
        List<TenantNotificationChannelIssueView> issues = new ArrayList<>();
        int tenantUsingNotificationChannels = 0;
        for (TenantInfo tenant : tenantRepository.findByIsDeleted(NOT_DELETED)) {
            List<String> selectedChannels = parseNotificationChannels(tenant.getNotificationChannels());
            if (selectedChannels.isEmpty()) {
                continue;
            }
            tenantUsingNotificationChannels += 1;
            List<String> unavailableChannels = resolveUnavailableChannels(selectedChannels, alertEnabled, configuredChannels);
            if (unavailableChannels.isEmpty()) {
                continue;
            }
            TenantNotificationChannelIssueView issue = new TenantNotificationChannelIssueView();
            issue.setTenantId(tenant.getTenantId());
            issue.setTenantName(tenant.getTenantName());
            issue.setSelectedChannels(selectedChannels);
            issue.setUnavailableChannels(unavailableChannels);
            issue.setReason(buildNotificationChannelIssueReason(alertEnabled, unavailableChannels));
            issue.setSuggestion(buildNotificationChannelIssueSuggestion(alertEnabled, unavailableChannels));
            issues.add(issue);
        }
        issues.sort(Comparator.comparing(TenantNotificationChannelIssueView::getTenantId, Comparator.nullsLast(String::compareTo)));

        TenantNotificationChannelHealthView view = new TenantNotificationChannelHealthView();
        view.setAlertEnabled(alertEnabled);
        view.setConfiguredChannels(configuredChannels);
        view.setUnconfiguredChannels(unconfiguredChannels);
        view.setTenantUsingNotificationChannels(tenantUsingNotificationChannels);
        view.setTenantIssueCount(issues.size());
        view.setSummary(buildNotificationChannelHealthSummary(alertEnabled, configuredChannels, issues));
        view.setIssues(issues);
        return view;
    }

    /**
     * 测试租户默认通知通道。
     *
     * @param request 测试请求
     * @return 测试结果
     * @author 余炘洋
     * @date 2026-04-15
     */
    public TenantNotificationChannelTestView testNotificationChannels(TenantNotificationChannelTestRequest request) {
        if (request == null) {
            throw new BizException(ErrorCode.INVALID_PARAM, "测试请求不能为空");
        }
        List<String> channels = parseNotificationChannels(normalizeNotificationChannels(request.getChannels()));
        if (channels.isEmpty()) {
            throw new BizException(ErrorCode.INVALID_PARAM, "至少选择一个默认通知通道");
        }
        LocalDateTime now = LocalDateTime.now();
        String tenantId = normalizeTenantId(request.getTenantId());
        String tenantName = normalizeText(request.getTenantName());
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("tenantId", StringUtils.hasText(tenantId) ? tenantId : "-");
        details.put("tenantName", StringUtils.hasText(tenantName) ? tenantName : "-");
        details.put("channels", channels);
        details.put("scene", "TENANT_NOTIFICATION_CHANNEL_TEST");
        details.put("testTime", now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));

        AlertDispatchResult dispatchResult = opsAlertService.sendAlertToChannelsWithResult(
                "租户通知通道测试",
                buildNotificationTestMessage(tenantId, tenantName, channels),
                details,
                channels
        );
        return toNotificationChannelTestView(tenantId, tenantName, channels, now, dispatchResult);
    }

    /**
     * 创建租户。
     *
     * @param request 创建请求
     * @return READY 开户回执
     * @author 余炘洋
     * @date 2026-01-26
     */
    @Transactional
    public TenantProvisioningReceipt createTenant(TenantCreateRequest request) {
        if (request == null) {
            throw new BizException(ErrorCode.INVALID_PARAM, "租户信息不能为空");
        }
        String tenantId = normalizeTenantId(request.getTenantId());
        if (!StringUtils.hasText(request.getTenantName())) {
            throw new BizException(ErrorCode.INVALID_PARAM, "租户名称不能为空");
        }
        validateAdminAccount(request.getAdminAccount());
        BillingPlan plan = requireReadyBillingPlan(request.getPlanName());
        TenantDbConfig dataSource = buildReadyDataSource(request.getDataSource());
        List<RbacPermission> basePermissions = resolveBasePermissions();
        tenantDataSourceConfigService.verifyReadOnlyConnection(dataSource);
        tenantId = resolveTenantIdForCreate(tenantId);
        TenantInfo tenant = new TenantInfo();
        tenant.setTenantId(tenantId);
        tenant.setTenantName(request.getTenantName().trim());
        tenant.setStatus(ACTIVE_STATUS);
        tenant.setRemark(request.getRemark());
        tenant.setContactName(request.getContactName());
        tenant.setContactPhone(request.getContactPhone());
        tenant.setContactEmail(request.getContactEmail());
        if (request.getNotificationChannels() != null) {
            tenant.setNotificationChannels(normalizeNotificationChannels(request.getNotificationChannels()));
        }
        tenant.setExpireTime(parseDateTime(request.getExpireTime()));
        tenant.setPlanName(plan.getPlanName());
        tenant.setQuotaBalance(plan.getDefaultQuotaBalance());
        tenant.setServiceLevel(resolveServiceLevel(plan.getServiceLevel()));
        tenant.setTenantType(resolveTenantType(plan.getTenantType()));
        tenantLifecycleService.applyDefaultExpireTimeForTrial(tenant);
        tenant.setIsDeleted(NOT_DELETED);
        tenant.setCreateTime(LocalDateTime.now());
        tenant.setUpdateTime(LocalDateTime.now());
        TenantInfo saved = tenantRepository.save(tenant);
        dataSource.setTenantId(saved.getTenantId());
        initializeTenant(saved, request, basePermissions, dataSource);
        refreshBalanceProjectionAfterCommit(saved.getTenantId(), saved.getQuotaBalance());
        return toProvisioningReceipt(saved, request.getAdminAccount(), dataSource);
    }

    private void validateAdminAccount(TenantAdminAccountRequest account) {
        if (account == null) {
            throw new BizException(ErrorCode.INVALID_PARAM, "必须配置租户管理员账号");
        }
        if (!StringUtils.hasText(account.getUsername())) {
            throw new BizException(ErrorCode.INVALID_PARAM, "租户管理员用户名不能为空");
        }
        if (!StringUtils.hasText(account.getPassword())) {
            throw new BizException(ErrorCode.INVALID_PARAM, "租户管理员初始密码不能为空");
        }
    }

    private BillingPlan requireReadyBillingPlan(String requestedPlanName) {
        String planName = normalizePlanName(requestedPlanName);
        if (!StringUtils.hasText(planName)) {
            throw new BizException(ErrorCode.INVALID_PARAM, "READY 开户必须选择套餐");
        }
        BillingPlan plan = resolveBillingPlan(planName);
        if (!ACTIVE_STATUS.equals(plan.getStatus())) {
            throw new BizException(ErrorCode.INVALID_PARAM, "READY 开户只能使用已启用套餐");
        }
        if (plan.getDefaultQuotaBalance() == null || plan.getDefaultQuotaBalance() <= 0) {
            throw new BizException(ErrorCode.INVALID_PARAM, "READY 开户套餐必须配置正数初始余额");
        }
        return plan;
    }

    private TenantDbConfig buildReadyDataSource(TenantDataSourceProvisionRequest request) {
        if (request == null) {
            throw new BizException(ErrorCode.INVALID_PARAM, "READY 开户必须配置数据源");
        }
        String jdbcUrl = normalizeText(request.getJdbcUrl());
        if (!StringUtils.hasText(jdbcUrl) || !jdbcUrl.toLowerCase(Locale.ROOT).startsWith("jdbc:mysql://")) {
            throw new BizException(ErrorCode.CONFIG_INVALID, "当前 READY 开户仅支持 MySQL JDBC 地址");
        }
        String username = normalizeText(request.getUsername());
        if (!StringUtils.hasText(username) || !StringUtils.hasText(request.getPassword())) {
            throw new BizException(ErrorCode.CONFIG_INVALID, "READY 开户必须配置数据库只读账号和密码");
        }
        String driverClass = normalizeText(request.getDriverClass());
        if (!StringUtils.hasText(driverClass)) {
            driverClass = MYSQL_DRIVER_CLASS;
        }
        if (!MYSQL_DRIVER_CLASS.equals(driverClass)) {
            throw new BizException(ErrorCode.CONFIG_INVALID, "当前 READY 开户仅支持 MySQL 驱动");
        }
        String isolationMode = normalizeText(request.getIsolationMode());
        if (!StringUtils.hasText(isolationMode)) {
            isolationMode = PHYSICAL_ISOLATION;
        } else {
            isolationMode = isolationMode.toUpperCase(Locale.ROOT);
        }
        if (!PHYSICAL_ISOLATION.equals(isolationMode) && !LOGICAL_ISOLATION.equals(isolationMode)) {
            throw new BizException(ErrorCode.CONFIG_INVALID, "数据隔离模式仅支持 PHYSICAL 或 LOGICAL");
        }
        String logicColumn = normalizeText(request.getLogicColumn());
        if (!StringUtils.hasText(logicColumn)) {
            logicColumn = "tenant_id";
        }
        if (LOGICAL_ISOLATION.equals(isolationMode) && !logicColumn.matches("[A-Za-z_][A-Za-z0-9_]{0,63}")) {
            throw new BizException(ErrorCode.CONFIG_INVALID, "逻辑隔离字段格式非法");
        }
        if (!StringUtils.hasText(request.getWhiteListTables())
                || !StringUtils.hasText(request.getWhiteListColumns())) {
            throw new BizException(ErrorCode.CONFIG_INVALID, "READY 开户必须配置表白名单和字段白名单");
        }
        TenantDbConfig config = new TenantDbConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(username);
        config.setPassword(request.getPassword());
        config.setDriverClass(driverClass);
        config.setIsolationMode(isolationMode);
        config.setLogicColumn(logicColumn);
        config.setWhiteListTables(request.getWhiteListTables().trim());
        config.setWhiteListColumns(request.getWhiteListColumns().trim());
        config.setSensitiveColumns(normalizeText(request.getSensitiveColumns()));
        config.setStatus(ACTIVE_STATUS);
        return config;
    }

    private TenantProvisioningReceipt toProvisioningReceipt(TenantInfo tenant,
                                                             TenantAdminAccountRequest account,
                                                             TenantDbConfig dataSource) {
        return new TenantProvisioningReceipt(
                tenant.getId(),
                tenant.getTenantId(),
                tenant.getTenantName(),
                READY_STATUS,
                account.getUsername().trim(),
                tenant.getPlanName(),
                tenant.getQuotaBalance(),
                dataSource.getIsolationMode(),
                true,
                true,
                true,
                true,
                true
        );
    }

    private void refreshBalanceProjectionAfterCommit(String tenantId, Long balance) {
        Runnable refresh = () -> billingBalanceService.resetBalance(tenantId, balance);
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            refresh.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                refresh.run();
            }
        });
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
        boolean activating = INACTIVE_STATUS.equals(tenant.getStatus())
                && ACTIVE_STATUS.equals(request.getStatus());
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
        tenant.setNotificationChannels(normalizeNotificationChannels(request.getNotificationChannels()));
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
        if (ACTIVE_STATUS.equals(tenant.getStatus())
                && (activating || planChanged || request.getQuotaBalance() != null)) {
            validateTenantReadyForActivation(tenant);
        }
        tenant.setUpdateTime(LocalDateTime.now());
        TenantInfo saved = tenantRepository.save(tenant);
        if (activating) {
            activateTenantConfig(saved.getTenantId());
        }
        if (request.getQuotaBalance() != null || resolvedQuotaBalance != null) {
            refreshBalanceProjectionAfterCommit(saved.getTenantId(), saved.getQuotaBalance());
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
            validateTenantReadyForActivation(tenant);
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
        validateTenantReadyForActivation(tenant);
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
    private void initializeTenant(TenantInfo tenant,
                                  TenantCreateRequest request,
                                  List<RbacPermission> basePermissions,
                                  TenantDbConfig dataSource) {
        RbacRole role = ensureTenantAdminRole(tenant.getTenantId());
        ensureTenantDataScope(role.getId());
        initTenantPermissionPool(tenant.getTenantId(), basePermissions);
        initRolePermissions(role.getId(), basePermissions);
        createTenantAdminAccount(tenant.getTenantId(), role.getId(), request.getAdminAccount());
        tenantDbConfigRepository.save(dataSource);
        initTenantOrganizationTemplate(tenant.getTenantId(), request);
    }

    /**
     * 创建租户首个管理员账号，账号与租户基础数据共享同一事务。
     *
     * @param tenantId 租户ID
     * @param roleId   租户管理员角色ID
     * @param account  管理员账号
     * @author 余炘洋
     * @date 2026-08-15
     */
    private void createTenantAdminAccount(String tenantId, Long roleId, TenantAdminAccountRequest account) {
        if (account == null) {
            throw new BizException(ErrorCode.INVALID_PARAM, "必须配置租户管理员账号");
        }
        UserCreateRequest userRequest = new UserCreateRequest();
        userRequest.setTenantId(tenantId);
        userRequest.setUsername(account.getUsername());
        userRequest.setPassword(account.getPassword());
        userRequest.setNickname(account.getNickname());
        userRequest.setStatus(ACTIVE_STATUS);
        userRequest.setIsExperience(0);
        userRequest.setRoleIds(List.of(roleId));
        userAdminService.createUser(userRequest, tenantId, true);
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
     * 初始化租户组织模板。
     *
     * <p>将“创建租户”和“初始化组织树”合并到同一条开户链路里，避免管理员
     * 先建租户、再切换到组织页做第二次初始化。</p>
     *
     * @param tenantId 租户ID
     * @param request  创建请求
     * @author 余炘洋
     * @date 2026-04-13
     */
    private void initTenantOrganizationTemplate(String tenantId, TenantCreateRequest request) {
        if (request == null || !StringUtils.hasText(request.getOrgTemplateCode())) {
            return;
        }
        OrgTemplateApplyRequest templateRequest = new OrgTemplateApplyRequest();
        templateRequest.setTenantId(tenantId);
        templateRequest.setTemplateCode(request.getOrgTemplateCode().trim());
        templateRequest.setRootUnitName(StringUtils.hasText(request.getOrgRootUnitName()) ? request.getOrgRootUnitName().trim() : null);
        organizationAdminService.applyTemplate(templateRequest, tenantId, false);
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
        Map<String, RbacPermission> permissionByCode = new LinkedHashMap<>();
        if (permissions != null) {
            for (RbacPermission permission : permissions) {
                if (permission != null
                        && StringUtils.hasText(permission.getPermCode())
                        && ACTIVE_STATUS.equals(permission.getStatus())) {
                    permissionByCode.put(permission.getPermCode(), permission);
                }
            }
        }
        List<String> missingCodes = BASE_PERMISSION_CODES.stream()
                .filter(code -> !permissionByCode.containsKey(code))
                .toList();
        if (!missingCodes.isEmpty()) {
            throw new BizException(ErrorCode.CONFIG_INVALID, "租户基础权限未完整初始化，请先执行权限基线迁移");
        }
        return BASE_PERMISSION_CODES.stream().map(permissionByCode::get).toList();
    }

    private void validateTenantReadyForActivation(TenantInfo tenant) {
        BillingPlan plan = requireReadyBillingPlan(tenant.getPlanName());
        if (tenant.getQuotaBalance() == null || tenant.getQuotaBalance() <= 0) {
            throw new BizException(ErrorCode.INVALID_PARAM, "租户余额不足，不能启用");
        }
        List<RbacPermission> basePermissions = resolveBasePermissions();
        List<Long> basePermissionIds = basePermissions.stream().map(RbacPermission::getId).toList();
        RbacRole adminRole = roleRepository.findByTenantIdAndRoleCode(tenant.getTenantId(), TENANT_ADMIN_ROLE_CODE)
                .filter(role -> ACTIVE_STATUS.equals(role.getStatus()))
                .orElseThrow(() -> new BizException(ErrorCode.CONFIG_INVALID, "租户管理员角色未就绪，不能启用"));
        List<Long> adminUserIds = userRoleRepository.findUserIdsByRoleId(adminRole.getId());
        boolean activeAdminExists = userRepository.findAllById(adminUserIds).stream()
                .anyMatch(user -> tenant.getTenantId().equals(user.getTenantId())
                        && ACTIVE_STATUS.equals(user.getStatus()));
        if (!activeAdminExists) {
            throw new BizException(ErrorCode.CONFIG_INVALID, "租户没有可登录管理员，不能启用");
        }
        if (!tenantPermissionRepository.findPermIdsByTenantId(tenant.getTenantId()).containsAll(basePermissionIds)
                || !rolePermissionRepository.findPermIdsByRoleIds(List.of(adminRole.getId()))
                .containsAll(basePermissionIds)) {
            throw new BizException(ErrorCode.CONFIG_INVALID, "租户基础权限未完整绑定，不能启用");
        }
        TenantDbConfig dataSource = tenantDbConfigRepository.findByTenantId(tenant.getTenantId())
                .orElseThrow(() -> new BizException(ErrorCode.CONFIG_INVALID, "租户数据源未配置，不能启用"));
        tenantDataSourceConfigService.verifyReadOnlyConnection(dataSource);
        if (!ACTIVE_STATUS.equals(plan.getStatus()) || plan.getDefaultQuotaBalance() == null) {
            throw new BizException(ErrorCode.CONFIG_INVALID, "租户套餐未就绪，不能启用");
        }
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
        view.setNotificationChannels(parseNotificationChannels(tenant.getNotificationChannels()));
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

    private TenantNotificationChannelTestView toNotificationChannelTestView(String tenantId,
                                                                            String tenantName,
                                                                            List<String> channels,
                                                                            LocalDateTime testTime,
                                                                            AlertDispatchResult dispatchResult) {
        TenantNotificationChannelTestView view = new TenantNotificationChannelTestView();
        view.setTenantId(tenantId);
        view.setTenantName(tenantName);
        view.setRequestedChannels(channels);
        view.setSuccess(dispatchResult != null && dispatchResult.success());
        view.setSummary(dispatchResult == null ? "未执行测试" : dispatchResult.summarize());
        view.setTestTime(testTime);
        view.setChannelResults(dispatchResult == null
                ? List.of()
                : dispatchResult.channelResults().stream().map(this::toNotificationChannelTestChannelView).toList());
        return view;
    }

    private TenantNotificationChannelTestChannelView toNotificationChannelTestChannelView(AlertDispatchChannelResult result) {
        TenantNotificationChannelTestChannelView view = new TenantNotificationChannelTestChannelView();
        view.setChannel(result.channel());
        view.setSuccess(result.success());
        view.setStatusCode(result.statusCode());
        view.setDetail(result.detail());
        return view;
    }

    private String buildNotificationTestMessage(String tenantId, String tenantName, List<String> channels) {
        String resolvedTenantId = StringUtils.hasText(tenantId) ? tenantId : "未指定";
        String resolvedTenantName = StringUtils.hasText(tenantName) ? tenantName : "未命名租户";
        return "这是一条来自租户管理后台的测试消息，用于验证默认通知通道是否可用。租户="
                + resolvedTenantName + "（" + resolvedTenantId + "），通道=" + String.join(",", channels);
    }

    private List<String> resolveUnavailableChannels(List<String> selectedChannels,
                                                    boolean alertEnabled,
                                                    List<String> configuredChannels) {
        if (!alertEnabled) {
            return selectedChannels;
        }
        return selectedChannels.stream()
                .filter(channel -> !configuredChannels.contains(channel))
                .toList();
    }

    private String buildNotificationChannelIssueReason(boolean alertEnabled, List<String> unavailableChannels) {
        if (!alertEnabled) {
            return "系统通知总开关未启用，当前租户默认通知通道不会生效。";
        }
        return "以下通道在系统侧尚未配置：" + String.join("、", unavailableChannels);
    }

    private String buildNotificationChannelIssueSuggestion(boolean alertEnabled, List<String> unavailableChannels) {
        if (!alertEnabled) {
            return "请先启用 lingsql.alert.enabled，再重新测试租户默认通知通道。";
        }
        return "请补齐 " + String.join("、", unavailableChannels) + " 的系统 Webhook 配置，或调整租户默认通知通道。";
    }

    private String buildNotificationChannelHealthSummary(boolean alertEnabled,
                                                         List<String> configuredChannels,
                                                         List<TenantNotificationChannelIssueView> issues) {
        if (!alertEnabled) {
            return "系统通知总开关当前关闭，所有租户默认通知通道均处于不可用状态。";
        }
        if (configuredChannels.isEmpty()) {
            return "系统通知总开关已开启，但尚未配置任何可用通道。";
        }
        if (issues.isEmpty()) {
            return "所有已配置默认通知通道的租户均通过预检。";
        }
        return "共有 " + issues.size() + " 个租户使用了当前不可用的默认通知通道，请优先处理。";
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

    private String normalizeNotificationChannels(List<String> channels) {
        if (channels == null || channels.isEmpty()) {
            return null;
        }
        List<String> normalized = channels.stream()
                .filter(StringUtils::hasText)
                .map(value -> value.trim().toLowerCase(Locale.ROOT))
                .distinct()
                .toList();
        for (String channel : normalized) {
            if (!SUPPORTED_NOTIFICATION_CHANNELS.contains(channel)) {
                throw new BizException(ErrorCode.INVALID_PARAM, "默认通知通道非法");
            }
        }
        return normalized.isEmpty() ? null : String.join(",", normalized);
    }

    private List<String> parseNotificationChannels(String value) {
        if (!StringUtils.hasText(value)) {
            return List.of();
        }
        return java.util.Arrays.stream(value.split(","))
                .filter(StringUtils::hasText)
                .map(item -> item.trim().toLowerCase(Locale.ROOT))
                .distinct()
                .toList();
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
