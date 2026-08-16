package com.lingsql.tenant.readiness;

import com.lingsql.agent.repository.TenantModelRouteRepository;
import com.lingsql.billing.entity.BillingPlan;
import com.lingsql.billing.repository.BillingPlanRepository;
import com.lingsql.common.exception.BizException;
import com.lingsql.common.exception.ErrorCode;
import com.lingsql.config.LingSqlProperties;
import com.lingsql.datasource.model.TenantDbConfig;
import com.lingsql.datasource.repository.TenantDbConfigRepository;
import com.lingsql.rbac.repository.RbacPermissionRepository;
import com.lingsql.rbac.repository.RbacUserRepository;
import com.lingsql.tenant.entity.TenantInfo;
import com.lingsql.tenant.model.TenantLifecycleSnapshot;
import com.lingsql.tenant.readiness.model.TenantFirstQuestionReadinessView;
import com.lingsql.tenant.readiness.model.TenantReadinessCheckView;
import com.lingsql.tenant.repository.TenantRepository;
import com.lingsql.tenant.service.TenantLifecycleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 租户首问就绪深模块，统一汇总开始真实问数前必须成立的事实。
 *
 * @author 余炘洋
 * @date 2026-08-16
 */
@Service
@RequiredArgsConstructor
public class TenantFirstQuestionReadiness {

    private static final int ACTIVE = 1;
    private static final int NOT_DELETED = 0;
    private static final int SCHEMA_SYNCED = 1;
    private static final String TENANT_ADMIN_ROLE = "TENANT_ADMIN";
    private static final String HEALTHY = "HEALTHY";
    private static final String READY_STATUS = "READY_TO_ASK";
    private static final String BLOCKED_STATUS = "BLOCKED";
    private static final Duration DEFAULT_PROBE_FRESHNESS_TTL = Duration.ofHours(24);
    private static final List<String> FIRST_QUESTION_PERMISSION_CODES = List.of(
            "MENU:CHAT",
            "CONFIG:CHAT:READ",
            "API:CHAT:ASK"
    );

    private final TenantRepository tenantRepository;
    private final TenantLifecycleService tenantLifecycleService;
    private final RbacUserRepository userRepository;
    private final RbacPermissionRepository permissionRepository;
    private final BillingPlanRepository billingPlanRepository;
    private final TenantDbConfigRepository dataSourceRepository;
    private final TenantModelRouteRepository modelRouteRepository;
    private final LingSqlProperties properties;

    /**
     * 评估指定租户是否具备开始真实问数的条件。
     *
     * @param tenantId 租户标识
     * @return 首问就绪视图
     */
    @Transactional(readOnly = true)
    public TenantFirstQuestionReadinessView evaluate(String tenantId) {
        String normalizedTenantId = normalizeTenantId(tenantId);
        Optional<TenantInfo> tenantOptional = tenantRepository
                .findByTenantIdAndIsDeleted(normalizedTenantId, NOT_DELETED);
        TenantInfo tenant = tenantOptional.orElse(null);
        List<TenantReadinessCheckView> checks = new ArrayList<>();

        checks.add(checkTenantActive(tenant));
        checks.add(checkLoginAdmin(normalizedTenantId, tenant != null));
        checks.add(checkBilling(tenant));
        checks.add(checkDataSource(normalizedTenantId, tenant != null));
        checks.add(checkModelRoute(normalizedTenantId, tenant != null));

        List<TenantReadinessCheckView> immutableChecks = List.copyOf(checks);
        List<String> failureReasons = immutableChecks.stream()
                .filter(check -> !check.passed())
                .map(TenantReadinessCheckView::failureReason)
                .toList();
        boolean ready = failureReasons.isEmpty();
        return new TenantFirstQuestionReadinessView(
                normalizedTenantId,
                tenant == null ? null : tenant.getTenantName(),
                ready ? READY_STATUS : BLOCKED_STATUS,
                ready,
                immutableChecks,
                failureReasons
        );
    }

    private TenantReadinessCheckView checkTenantActive(TenantInfo tenant) {
        if (tenant == null) {
            return failed("TENANT_ACTIVE", "租户状态", "租户不存在或已删除");
        }
        TenantLifecycleSnapshot lifecycle = tenantLifecycleService.evaluate(tenant);
        if (!Integer.valueOf(ACTIVE).equals(tenant.getStatus()) || !lifecycle.accessAllowed()) {
            return failed("TENANT_ACTIVE", "租户状态", "租户未启用或生命周期不允许访问核心能力");
        }
        return passed("TENANT_ACTIVE", "租户状态");
    }

    private TenantReadinessCheckView checkLoginAdmin(String tenantId, boolean tenantExists) {
        if (!tenantExists) {
            return failed("TENANT_ADMIN_LOGIN", "租户管理员", "租户不存在，无法校验管理员账号");
        }
        long count = userRepository.countLoginReadyUsersByTenantAndRole(
                tenantId,
                TENANT_ADMIN_ROLE,
                ACTIVE,
                LocalDateTime.now()
        );
        if (count < 1) {
            return failed("TENANT_ADMIN_LOGIN", "租户管理员", "不存在已启用、未锁定且绑定有效 TENANT_ADMIN 角色的账号");
        }
        List<String> effectivePermissionCodes = permissionRepository.findEffectiveTenantRolePermissionCodes(
                tenantId,
                TENANT_ADMIN_ROLE,
                ACTIVE,
                FIRST_QUESTION_PERMISSION_CODES
        );
        List<String> missingPermissionCodes = FIRST_QUESTION_PERMISSION_CODES.stream()
                .filter(code -> !effectivePermissionCodes.contains(code))
                .toList();
        if (!missingPermissionCodes.isEmpty()) {
            return failed(
                    "TENANT_ADMIN_LOGIN",
                    "租户管理员",
                    "TENANT_ADMIN 角色授权或租户权限池缺少首问权限: " + String.join(", ", missingPermissionCodes)
            );
        }
        return passed("TENANT_ADMIN_LOGIN", "租户管理员");
    }

    private TenantReadinessCheckView checkBilling(TenantInfo tenant) {
        if (tenant == null) {
            return failed("BILLING_AVAILABLE", "套餐与余额", "租户不存在，无法校验套餐与余额");
        }
        if (tenant.getQuotaBalance() == null || tenant.getQuotaBalance() <= 0) {
            return failed("BILLING_AVAILABLE", "套餐与余额", "租户可用余额必须大于 0");
        }
        Optional<BillingPlan> plan = StringUtils.hasText(tenant.getPlanName())
                ? billingPlanRepository.findByPlanNameAndIsDeleted(tenant.getPlanName().trim(), NOT_DELETED)
                : Optional.empty();
        if (plan.isEmpty() || !Integer.valueOf(ACTIVE).equals(plan.get().getStatus())) {
            return failed("BILLING_AVAILABLE", "套餐与余额", "租户未绑定有效且已启用的套餐");
        }
        return passed("BILLING_AVAILABLE", "套餐与余额");
    }

    private TenantReadinessCheckView checkDataSource(String tenantId, boolean tenantExists) {
        if (!tenantExists) {
            return failed("DATASOURCE_SCHEMA_READY", "数据源与 Schema", "租户不存在，无法校验数据源");
        }
        Optional<TenantDbConfig> config = dataSourceRepository.findByTenantId(tenantId);
        if (config.isEmpty() || !Integer.valueOf(ACTIVE).equals(config.get().getStatus())) {
            return failed("DATASOURCE_SCHEMA_READY", "数据源与 Schema", "租户未配置已启用的数据源");
        }
        if (!Integer.valueOf(SCHEMA_SYNCED).equals(config.get().getSchemaSyncStatus())
                || config.get().getLastSyncTime() == null
                || config.get().getSchemaTableCount() == null
                || config.get().getSchemaTableCount() <= 0
                || config.get().getSchemaWhitelistTableCount() == null
                || config.get().getSchemaWhitelistTableCount() <= 0
                || config.get().getSchemaTableCount() < config.get().getSchemaWhitelistTableCount()
                || !StringUtils.hasText(config.get().getSchemaWhitelistDigest())) {
            return failed("DATASOURCE_SCHEMA_READY", "数据源与 Schema", "数据源 Schema 尚未同步或已被标记为待刷新");
        }
        return passed("DATASOURCE_SCHEMA_READY", "数据源与 Schema");
    }

    private TenantReadinessCheckView checkModelRoute(String tenantId, boolean tenantExists) {
        if (!tenantExists) {
            return failed("MODEL_ROUTE_READY", "模型授权路由", "租户不存在，无法校验模型授权路由");
        }
        long usableRoutes = modelRouteRepository.countUsableRoutes(
                tenantId,
                ACTIVE,
                HEALTHY,
                LocalDateTime.now().minus(resolveProbeFreshnessTtl())
        );
        if (usableRoutes < 1) {
            return failed(
                    "MODEL_ROUTE_READY",
                    "模型授权路由",
                    "不存在已启用且探测结果仍在有效期内的 HEALTHY 授权模型，请重新执行模型验证"
            );
        }
        return passed("MODEL_ROUTE_READY", "模型授权路由");
    }

    private Duration resolveProbeFreshnessTtl() {
        Duration configured = properties.getModel() == null
                ? null
                : properties.getModel().getProbeFreshnessTtl();
        if (configured == null || configured.isZero() || configured.isNegative()) {
            return DEFAULT_PROBE_FRESHNESS_TTL;
        }
        return configured;
    }

    private TenantReadinessCheckView passed(String code, String name) {
        return new TenantReadinessCheckView(code, name, true, null);
    }

    private TenantReadinessCheckView failed(String code, String name, String reason) {
        return new TenantReadinessCheckView(code, name, false, reason);
    }

    private String normalizeTenantId(String tenantId) {
        if (!StringUtils.hasText(tenantId)
                || !tenantId.trim().matches("[A-Za-z0-9][A-Za-z0-9_-]{0,63}")) {
            throw new BizException(ErrorCode.INVALID_PARAM, "租户标识不合法");
        }
        return tenantId.trim();
    }
}
