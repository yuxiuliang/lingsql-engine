package com.lingsql.billing;

import com.lingsql.audit.model.SqlExecuteLogCommand;
import com.lingsql.audit.repository.SqlExecuteLogRepository;
import com.lingsql.tenant.entity.TenantInfo;
import com.lingsql.tenant.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 租户计费服务。
 *
 * @author 余炘洋
 * @date 2026-01-27
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantBillingService {

    private static final Integer NOT_DELETED = 0;
    private static final Integer ACTIVE_STATUS = 1;

    private final TenantRepository tenantRepository;
    private final SqlExecuteLogRepository sqlExecuteLogRepository;
    private final BillingPolicy billingPolicy;

    /**
     * 校验租户是否允许继续调用。
     *
     * @param tenantId 租户ID
     * @return 校验结果
     * @author 余炘洋
     * @date 2026-01-27
     */
    public BillingCheckResult checkAccess(String tenantId) {
        if (!StringUtils.hasText(tenantId)) {
            return BillingCheckResult.allow();
        }
        TenantInfo tenant = tenantRepository.findByTenantIdAndIsDeleted(tenantId, NOT_DELETED).orElse(null);
        if (tenant == null || !ACTIVE_STATUS.equals(tenant.getStatus())) {
            return BillingCheckResult.deny("请升级套餐");
        }
        long balance = resolveBalance(tenant.getQuotaBalance());
        if (balance <= 0) {
            return BillingCheckResult.deny("请升级套餐");
        }
        int dailyLimit = billingPolicy.resolveDailyLimit(tenant.getServiceLevel());
        if (dailyLimit > 0) {
            LocalDate today = LocalDate.now();
            LocalDateTime start = today.atStartOfDay();
            LocalDateTime end = today.plusDays(1).atStartOfDay().minusNanos(1);
            long used = sqlExecuteLogRepository.countByTenantIdAndCreateTimeBetween(tenantId, start, end);
            if (used >= dailyLimit) {
                return BillingCheckResult.deny("请升级套餐");
            }
        }
        return BillingCheckResult.allow();
    }

    /**
     * 扣费并写入成本字段。
     *
     * @param tenantId 租户ID
     * @param command  日志命令
     * @return 扣费结果
     * @author 余炘洋
     * @date 2026-01-27
     */
    @Transactional
    public BillingChargeResult charge(String tenantId, SqlExecuteLogCommand command) {
        if (!StringUtils.hasText(tenantId) || command == null) {
            return BillingChargeResult.skip();
        }
        TenantInfo tenant = tenantRepository.findByTenantIdAndIsDeleted(tenantId, NOT_DELETED).orElse(null);
        if (tenant == null) {
            return BillingChargeResult.skip();
        }
        long costAmount = billingPolicy.calculateCost(command.getModelName(), command.getTokenCount());
        command.setCostAmount(costAmount);
        long balance = resolveBalance(tenant.getQuotaBalance());
        long newBalance = balance - costAmount;
        if (newBalance < 0) {
            log.warn("租户余额不足，已透支: tenantId={}, balance={}, cost={}", tenantId, balance, costAmount);
            newBalance = 0;
        }
        tenant.setQuotaBalance(newBalance);
        tenantRepository.save(tenant);
        return BillingChargeResult.success(costAmount, balance, newBalance);
    }

    /**
     * 解析余额。
     *
     * @param quotaBalance 余额
     * @return 余额
     * @author 余炘洋
     * @date 2026-01-27
     */
    private long resolveBalance(Long quotaBalance) {
        if (quotaBalance == null || quotaBalance < 0) {
            return 0;
        }
        return quotaBalance;
    }
}
