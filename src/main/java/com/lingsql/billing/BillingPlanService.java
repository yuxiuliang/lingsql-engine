package com.lingsql.billing;

import com.lingsql.billing.entity.BillingPlan;
import com.lingsql.billing.model.BillingPlanCreateRequest;
import com.lingsql.billing.model.BillingPlanUpdateRequest;
import com.lingsql.billing.model.BillingPlanView;
import com.lingsql.billing.repository.BillingPlanRepository;
import com.lingsql.common.exception.BizException;
import com.lingsql.common.exception.ErrorCode;
import com.lingsql.tenant.entity.TenantInfo;
import com.lingsql.tenant.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 套餐管理服务。
 *
 * @author 余炘洋
 * @date 2026-01-29
 */
@Service
@RequiredArgsConstructor
public class BillingPlanService {

    private static final Integer NOT_DELETED = 0;
    private static final Integer DELETED = 1;
    private static final Integer ACTIVE_STATUS = 1;
    private static final Integer INACTIVE_STATUS = 0;
    private static final String PLAN_CODE_PREFIX = "PLAN";
    private static final int MAX_GENERATE_ATTEMPTS = 5;
    private static final long DEFAULT_QUOTA_BALANCE = 0L;

    private final BillingPlanRepository planRepository;
    private final TenantRepository tenantRepository;
    private final BillingBalanceService billingBalanceService;

    /**
     * 查询套餐列表。
     *
     * @param keyword 关键字
     * @param status  状态过滤
     * @return 套餐列表
     * @author 余炘洋
     * @date 2026-01-29
     */
    public List<BillingPlanView> listPlans(String keyword, Integer status) {
        List<BillingPlan> plans = planRepository.findByIsDeleted(NOT_DELETED);
        String normalizedKeyword = normalizeKeyword(keyword);
        return plans.stream()
                .filter(plan -> matchesStatus(plan, status))
                .filter(plan -> matchesKeyword(plan, normalizedKeyword))
                .sorted(Comparator.comparing(BillingPlan::getUpdateTime, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .map(this::toView)
                .toList();
    }

    /**
     * 创建套餐。
     *
     * @param request 创建请求
     * @return 套餐视图
     * @author 余炘洋
     * @date 2026-01-29
     */
    @Transactional
    public BillingPlanView createPlan(BillingPlanCreateRequest request) {
        if (request == null) {
            throw new BizException(ErrorCode.INVALID_PARAM, "套餐信息不能为空");
        }
        String planName = normalizeName(request.getPlanName());
        if (!StringUtils.hasText(planName)) {
            throw new BizException(ErrorCode.INVALID_PARAM, "套餐名称不能为空");
        }
        String planCode = normalizeCode(request.getPlanCode());
        if (!StringUtils.hasText(planCode)) {
            planCode = generatePlanCode();
        }
        if (planRepository.existsByPlanCodeAndIsDeleted(planCode, NOT_DELETED)) {
            throw new BizException(ErrorCode.CONFLICT, "套餐编码已存在");
        }
        if (planRepository.existsByPlanNameAndIsDeleted(planName, NOT_DELETED)) {
            throw new BizException(ErrorCode.CONFLICT, "套餐名称已存在");
        }
        BillingPlan plan = new BillingPlan();
        plan.setPlanCode(planCode);
        plan.setPlanName(planName);
        plan.setStatus(resolveStatus(request.getStatus()));
        plan.setServiceLevel(normalizeOptional(request.getServiceLevel()));
        plan.setTenantType(normalizeOptional(request.getTenantType()));
        plan.setDefaultQuotaBalance(resolveDefaultQuotaBalance(request.getDefaultQuotaBalance()));
        plan.setRemark(request.getRemark());
        plan.setIsDeleted(NOT_DELETED);
        LocalDateTime now = LocalDateTime.now();
        plan.setCreateTime(now);
        plan.setUpdateTime(now);
        return toView(planRepository.save(plan));
    }

    /**
     * 更新套餐。
     *
     * @param id      套餐ID
     * @param request 更新请求
     * @return 套餐视图
     * @author 余炘洋
     * @date 2026-01-29
     */
    @Transactional
    public BillingPlanView updatePlan(Long id, BillingPlanUpdateRequest request) {
        if (id == null) {
            throw new BizException(ErrorCode.INVALID_PARAM, "套餐ID不能为空");
        }
        if (request == null) {
            throw new BizException(ErrorCode.INVALID_PARAM, "套餐信息不能为空");
        }
        BillingPlan plan = planRepository.findByIdAndIsDeleted(id, NOT_DELETED)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "套餐不存在"));
        String previousServiceLevel = plan.getServiceLevel();
        String previousTenantType = plan.getTenantType();
        long previousDefaultQuotaBalance = resolveDefaultQuotaBalance(plan.getDefaultQuotaBalance());
        if (request.getPlanCode() != null) {
            String normalized = normalizeCode(request.getPlanCode());
            if (!StringUtils.hasText(normalized)) {
                throw new BizException(ErrorCode.INVALID_PARAM, "套餐编码不能为空");
            }
            if (!normalized.equals(plan.getPlanCode())
                    && planRepository.existsByPlanCodeAndIsDeleted(normalized, NOT_DELETED)) {
                throw new BizException(ErrorCode.CONFLICT, "套餐编码已存在");
            }
            plan.setPlanCode(normalized);
        }
        if (request.getPlanName() != null) {
            String normalized = normalizeName(request.getPlanName());
            if (!StringUtils.hasText(normalized)) {
                throw new BizException(ErrorCode.INVALID_PARAM, "套餐名称不能为空");
            }
            if (!normalized.equals(plan.getPlanName())) {
                long tenantCount = tenantRepository.countByPlanNameAndIsDeleted(plan.getPlanName(), NOT_DELETED);
                if (tenantCount > 0) {
                    throw new BizException(ErrorCode.BILLING_PLAN_CONFLICT, "套餐已被租户使用，无法修改名称");
                }
                if (planRepository.existsByPlanNameAndIsDeleted(normalized, NOT_DELETED)) {
                    throw new BizException(ErrorCode.CONFLICT, "套餐名称已存在");
                }
                plan.setPlanName(normalized);
            }
        }
        if (request.getStatus() != null) {
            plan.setStatus(resolveStatus(request.getStatus()));
        }
        if (request.getServiceLevel() != null) {
            plan.setServiceLevel(normalizeOptional(request.getServiceLevel()));
        }
        if (request.getTenantType() != null) {
            plan.setTenantType(normalizeOptional(request.getTenantType()));
        }
        if (request.getDefaultQuotaBalance() != null) {
            plan.setDefaultQuotaBalance(resolveDefaultQuotaBalance(request.getDefaultQuotaBalance()));
        }
        if (request.getRemark() != null) {
            plan.setRemark(request.getRemark());
        }
        plan.setUpdateTime(LocalDateTime.now());
        BillingPlan saved = planRepository.save(plan);
        syncTenantPlanDefaults(saved, previousServiceLevel, previousTenantType, previousDefaultQuotaBalance);
        return toView(saved);
    }

    /**
     * 删除套餐。
     *
     * @param id 套餐ID
     * @author 余炘洋
     * @date 2026-01-29
     */
    @Transactional
    public void deletePlan(Long id) {
        if (id == null) {
            throw new BizException(ErrorCode.INVALID_PARAM, "套餐ID不能为空");
        }
        BillingPlan plan = planRepository.findByIdAndIsDeleted(id, NOT_DELETED)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "套餐不存在"));
        long tenantCount = tenantRepository.countByPlanNameAndIsDeleted(plan.getPlanName(), NOT_DELETED);
        if (tenantCount > 0) {
            throw new BizException(ErrorCode.BILLING_PLAN_CONFLICT, "套餐已被租户使用，无法删除");
        }
        plan.setIsDeleted(DELETED);
        plan.setUpdateTime(LocalDateTime.now());
        planRepository.save(plan);
    }

    private String normalizeKeyword(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return null;
        }
        return keyword.trim().toLowerCase(Locale.ROOT);
    }

    private boolean matchesStatus(BillingPlan plan, Integer status) {
        if (status == null) {
            return true;
        }
        return status.equals(plan.getStatus());
    }

    private boolean matchesKeyword(BillingPlan plan, String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return true;
        }
        String name = plan.getPlanName() == null ? "" : plan.getPlanName().toLowerCase(Locale.ROOT);
        String code = plan.getPlanCode() == null ? "" : plan.getPlanCode().toLowerCase(Locale.ROOT);
        return name.contains(keyword) || code.contains(keyword);
    }

    private String normalizeName(String planName) {
        return planName == null ? null : planName.trim();
    }

    private String normalizeCode(String planCode) {
        if (!StringUtils.hasText(planCode)) {
            return null;
        }
        return planCode.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeOptional(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private int resolveStatus(Integer status) {
        if (status == null) {
            return ACTIVE_STATUS;
        }
        if (status != ACTIVE_STATUS && status != INACTIVE_STATUS) {
            throw new BizException(ErrorCode.INVALID_PARAM, "状态值非法");
        }
        return status;
    }

    private long resolveDefaultQuotaBalance(Long defaultQuotaBalance) {
        if (defaultQuotaBalance == null) {
            return DEFAULT_QUOTA_BALANCE;
        }
        if (defaultQuotaBalance < 0) {
            throw new BizException(ErrorCode.INVALID_PARAM, "默认余额不能为负数");
        }
        return defaultQuotaBalance;
    }

    private String generatePlanCode() {
        int attempts = 0;
        String planCode = buildPlanCode();
        while (planRepository.existsByPlanCodeAndIsDeleted(planCode, NOT_DELETED)) {
            if (attempts++ >= MAX_GENERATE_ATTEMPTS) {
                throw new BizException(ErrorCode.BUSINESS_ERROR, "套餐编码生成失败，请重试");
            }
            planCode = buildPlanCode();
        }
        return planCode;
    }

    private String buildPlanCode() {
        String timestamp = DateTimeFormatter.ofPattern("yyyyMMddHHmmss", Locale.ROOT)
                .format(LocalDateTime.now());
        int random = ThreadLocalRandom.current().nextInt(1000, 10000);
        return PLAN_CODE_PREFIX + timestamp + random;
    }

    /**
     * 同步仍跟随套餐默认值的租户配置。
     *
     * @param plan                       更新后的套餐
     * @param previousServiceLevel       更新前服务等级
     * @param previousTenantType         更新前租户类型
     * @param previousDefaultQuotaBalance 更新前默认余额
     * @author 余炘洋
     * @date 2026-04-10
     */
    private void syncTenantPlanDefaults(BillingPlan plan,
                                        String previousServiceLevel,
                                        String previousTenantType,
                                        long previousDefaultQuotaBalance) {
        if (plan == null || !StringUtils.hasText(plan.getPlanName())) {
            return;
        }
        List<TenantInfo> tenants = tenantRepository.findByPlanNameAndIsDeleted(plan.getPlanName(), NOT_DELETED);
        if (tenants.isEmpty()) {
            return;
        }
        String normalizedCurrentServiceLevel = normalizeOptional(plan.getServiceLevel());
        String normalizedCurrentTenantType = normalizeOptional(plan.getTenantType());
        String normalizedPreviousServiceLevel = normalizeOptional(previousServiceLevel);
        String normalizedPreviousTenantType = normalizeOptional(previousTenantType);
        long currentDefaultQuotaBalance = resolveDefaultQuotaBalance(plan.getDefaultQuotaBalance());
        boolean serviceLevelChanged = !Objects.equals(normalizedCurrentServiceLevel, normalizedPreviousServiceLevel);
        boolean tenantTypeChanged = !Objects.equals(normalizedCurrentTenantType, normalizedPreviousTenantType);
        boolean defaultQuotaChanged = currentDefaultQuotaBalance != previousDefaultQuotaBalance;
        if (!serviceLevelChanged && !tenantTypeChanged && !defaultQuotaChanged) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        List<TenantInfo> changedTenants = new ArrayList<>();
        List<TenantInfo> balanceChangedTenants = new ArrayList<>();
        for (TenantInfo tenant : tenants) {
            boolean changed = false;
            boolean balanceChanged = false;
            if (serviceLevelChanged && shouldSyncServiceLevel(tenant.getServiceLevel(), normalizedPreviousServiceLevel)) {
                tenant.setServiceLevel(resolveTenantServiceLevel(plan.getServiceLevel()));
                changed = true;
            }
            if (tenantTypeChanged && shouldSyncTenantType(tenant.getTenantType(), normalizedPreviousTenantType)) {
                tenant.setTenantType(resolveTenantType(plan.getTenantType()));
                changed = true;
            }
            if (defaultQuotaChanged && shouldSyncQuotaBalance(tenant.getQuotaBalance(), previousDefaultQuotaBalance)) {
                tenant.setQuotaBalance(currentDefaultQuotaBalance);
                changed = true;
                balanceChanged = true;
            }
            if (!changed) {
                continue;
            }
            tenant.setUpdateTime(now);
            changedTenants.add(tenant);
            if (balanceChanged) {
                balanceChangedTenants.add(tenant);
            }
        }
        if (changedTenants.isEmpty()) {
            return;
        }
        tenantRepository.saveAll(changedTenants);
        for (TenantInfo tenant : balanceChangedTenants) {
            billingBalanceService.resetBalance(tenant.getTenantId(), tenant.getQuotaBalance());
        }
    }

    /**
     * 判断服务等级是否仍跟随套餐。
     *
     * @param tenantServiceLevel   租户服务等级
     * @param previousServiceLevel 更新前套餐服务等级
     * @return 是否允许同步
     * @author 余炘洋
     * @date 2026-04-10
     */
    private boolean shouldSyncServiceLevel(String tenantServiceLevel, String previousServiceLevel) {
        String normalizedTenantServiceLevel = normalizeOptional(tenantServiceLevel);
        if (!StringUtils.hasText(normalizedTenantServiceLevel)) {
            return true;
        }
        return Objects.equals(normalizedTenantServiceLevel, previousServiceLevel);
    }

    /**
     * 判断租户类型是否仍跟随套餐。
     *
     * @param tenantTenantType   租户类型
     * @param previousTenantType 更新前套餐租户类型
     * @return 是否允许同步
     * @author 余炘洋
     * @date 2026-04-10
     */
    private boolean shouldSyncTenantType(String tenantTenantType, String previousTenantType) {
        String normalizedTenantType = normalizeOptional(tenantTenantType);
        if (!StringUtils.hasText(normalizedTenantType)) {
            return true;
        }
        return Objects.equals(normalizedTenantType, previousTenantType);
    }

    /**
     * 判断余额是否仍跟随套餐默认值。
     *
     * @param tenantQuotaBalance          租户当前余额
     * @param previousDefaultQuotaBalance 更新前默认余额
     * @return 是否允许同步
     * @author 余炘洋
     * @date 2026-04-10
     */
    private boolean shouldSyncQuotaBalance(Long tenantQuotaBalance, long previousDefaultQuotaBalance) {
        if (tenantQuotaBalance == null || tenantQuotaBalance <= 0) {
            return true;
        }
        return tenantQuotaBalance == previousDefaultQuotaBalance;
    }

    /**
     * 解析租户服务等级。
     *
     * @param serviceLevel 服务等级
     * @return 规范化等级
     * @author 余炘洋
     * @date 2026-04-10
     */
    private String resolveTenantServiceLevel(String serviceLevel) {
        if (!StringUtils.hasText(serviceLevel)) {
            return BillingPolicy.DEFAULT_SERVICE_LEVEL;
        }
        return serviceLevel.trim().toUpperCase(Locale.ROOT);
    }

    /**
     * 解析租户类型。
     *
     * @param tenantType 租户类型
     * @return 规范化类型
     * @author 余炘洋
     * @date 2026-04-10
     */
    private String resolveTenantType(String tenantType) {
        if (!StringUtils.hasText(tenantType)) {
            return null;
        }
        return tenantType.trim().toUpperCase(Locale.ROOT);
    }

    private BillingPlanView toView(BillingPlan plan) {
        if (plan == null) {
            return null;
        }
        BillingPlanView view = new BillingPlanView();
        view.setId(plan.getId());
        view.setPlanCode(plan.getPlanCode());
        view.setPlanName(plan.getPlanName());
        view.setStatus(plan.getStatus());
        view.setServiceLevel(plan.getServiceLevel());
        view.setTenantType(plan.getTenantType());
        view.setDefaultQuotaBalance(plan.getDefaultQuotaBalance());
        view.setRemark(plan.getRemark());
        view.setCreateTime(plan.getCreateTime());
        view.setUpdateTime(plan.getUpdateTime());
        return view;
    }
}
