package com.lingsql.billing;

import com.lingsql.audit.model.SqlExecuteLogCommand;
import com.lingsql.audit.repository.SqlExecuteLogRepository;
import com.lingsql.billing.entity.TenantBillingOverride;
import com.lingsql.billing.model.BillingLimit;
import com.lingsql.billing.model.BillingPolicySnapshot;
import com.lingsql.billing.model.BillingQuote;
import com.lingsql.billing.repository.TenantBillingOverrideRepository;
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
    private static final Integer OVERRIDE_ACTIVE_STATUS = 1;
    private static final String MESSAGE_UPGRADE = "请升级套餐";
    private static final String MESSAGE_DAILY_LIMIT_REACHED = "今日调用次数已达上限";
    private static final String MESSAGE_DAILY_TOKEN_LIMIT_REACHED = "今日Token已达上限";

    private final TenantRepository tenantRepository;
    private final SqlExecuteLogRepository sqlExecuteLogRepository;
    private final BillingPolicy billingPolicy;
    private final TenantBillingOverrideRepository tenantBillingOverrideRepository;

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
            return BillingCheckResult.deny(MESSAGE_UPGRADE);
        }
        long balance = resolveBalance(tenant.getQuotaBalance());
        if (balance <= 0) {
            return BillingCheckResult.deny(MESSAGE_UPGRADE);
        }
        BillingPolicySnapshot snapshot = billingPolicy.currentSnapshot();
        BillingLimit limit = snapshot.resolveLimit(tenant.getServiceLevel());
        if (limit.getDailyCallLimit() > 0 || limit.getDailyTokenLimit() > 0) {
            LocalDate today = LocalDate.now();
            LocalDateTime start = today.atStartOfDay();
            LocalDateTime end = today.plusDays(1).atStartOfDay().minusNanos(1);
            if (limit.getDailyCallLimit() > 0) {
                long used = sqlExecuteLogRepository.countByTenantIdAndCreateTimeBetween(tenantId, start, end);
                if (used >= limit.getDailyCallLimit()) {
                    return BillingCheckResult.deny(MESSAGE_DAILY_LIMIT_REACHED);
                }
            }
            if (limit.getDailyTokenLimit() > 0) {
                long usedTokens = sqlExecuteLogRepository.sumTokenCountByTenantIdAndCreateTimeBetween(tenantId, start, end);
                if (usedTokens >= limit.getDailyTokenLimit()) {
                    return BillingCheckResult.deny(MESSAGE_DAILY_TOKEN_LIMIT_REACHED);
                }
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
        Integer tokenCount = command.getTokenCount();
        if (tokenCount == null || tokenCount <= 0) {
            return BillingChargeResult.skip();
        }
        TenantInfo tenant = tenantRepository.findByTenantIdAndIsDeletedForUpdate(tenantId, NOT_DELETED).orElse(null);
        if (tenant == null) {
            return BillingChargeResult.skip();
        }
        TenantBillingOverride override = tenantBillingOverrideRepository
                .findByTenantIdAndStatus(tenantId, OVERRIDE_ACTIVE_STATUS)
                .orElse(null);
        BillingQuote quote = billingPolicy.quote(
                command.getModelName(),
                command.getTokenCount(),
                tenant.getTenantType(),
                override == null ? null : override.getPriceMultiplier()
        );
        long costAmount = quote.costAmount();
        command.setCostAmount(costAmount);
        command.setBillingVersion(quote.policyVersion());
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
