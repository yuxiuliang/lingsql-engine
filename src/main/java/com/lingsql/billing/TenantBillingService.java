package com.lingsql.billing;

import com.lingsql.audit.model.SqlExecuteLogCommand;
import com.lingsql.audit.repository.SqlExecuteLogRepository;
import com.lingsql.billing.entity.TenantBillingOverride;
import com.lingsql.billing.model.BillingChargeItem;
import com.lingsql.billing.model.BillingLimit;
import com.lingsql.billing.model.BillingPolicySnapshot;
import com.lingsql.billing.model.BillingQuote;
import com.lingsql.billing.repository.TenantBillingOverrideRepository;
import com.lingsql.tenant.entity.TenantInfo;
import com.lingsql.tenant.model.TenantLifecycleSnapshot;
import com.lingsql.tenant.repository.TenantRepository;
import com.lingsql.tenant.service.TenantLifecycleService;
import com.lingsql.common.exception.BizException;
import com.lingsql.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
    private final BillingLedgerService billingLedgerService;
    private final TenantLifecycleService tenantLifecycleService;

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
        TenantLifecycleSnapshot lifecycle = tenantLifecycleService.evaluate(tenant);
        if (!lifecycle.accessAllowed()) {
            return BillingCheckResult.deny(tenantLifecycleService.resolveDenyMessage(lifecycle));
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
        TenantInfo tenant = tenantRepository.findByTenantIdAndIsDeleted(tenantId, NOT_DELETED).orElse(null);
        if (tenant == null) {
            return BillingChargeResult.skip();
        }
        Map<String, Integer> tokenUsage = resolveTokenUsage(command);
        if (tokenUsage.isEmpty()) {
            return BillingChargeResult.skip();
        }
        long reserved = resolveReservedCost(command.getReservedCostAmount());
        if (reserved <= 0) {
            throw new BizException(ErrorCode.BILLING_INVALID, "模型调用缺少有效预扣记录，请人工核对账本");
        }
        String requestId = resolveRequestId(command);
        int closeSeq = command.nextBillingEventSeq();
        BillingChargeResult closeResult = billingLedgerService.closeAppliedReservation(
                requestId,
                tenantId,
                closeSeq,
                tokenUsage,
                tenant.getQuotaBalance()
        );
        long costAmount = closeResult.costAmount();
        command.setCostAmount(costAmount);
        return BillingChargeResult.success(costAmount, reserved, reserved - costAmount);
    }

    /**
     * 预扣费用，余额不足时直接拒绝。
     *
     * @param tenantId 租户ID
     * @param command  日志命令
     * @author 余炘洋
     * @date 2026-01-29
     */
    public void reserveCost(String tenantId, SqlExecuteLogCommand command) {
        if (!StringUtils.hasText(tenantId) || command == null) {
            return;
        }
        TenantInfo tenant = tenantRepository.findByTenantIdAndIsDeleted(tenantId, NOT_DELETED).orElse(null);
        if (tenant == null) {
            return;
        }
        Map<String, Integer> tokenUsage = resolveTokenUsage(command);
        if (tokenUsage.isEmpty()) {
            return;
        }
        Map<String, Integer> reservedUsage = resolveReservedUsage(command);
        Map<String, Integer> deltaUsage = resolveDeltaUsage(tokenUsage, reservedUsage);
        if (deltaUsage.isEmpty()) {
            return;
        }
        TenantBillingOverride override = tenantBillingOverrideRepository
                .findByTenantIdAndStatus(tenantId, OVERRIDE_ACTIVE_STATUS)
                .orElse(null);
        List<BillingChargeItem> totalItems = buildChargeItems(tokenUsage, tenant, override);
        if (totalItems.isEmpty()) {
            return;
        }
        long totalCost = totalItems.stream().mapToLong(BillingChargeItem::costAmount).sum();
        if (totalCost <= 0) {
            return;
        }
        List<BillingChargeItem> deltaItems = buildChargeItems(deltaUsage, tenant, override);
        long deltaCost = deltaItems.stream().mapToLong(BillingChargeItem::costAmount).sum();
        if (deltaCost <= 0) {
            applyReservedUsage(command, deltaUsage);
            command.setReservedCostAmount(totalCost);
            command.setCostAmount(totalCost);
            command.setBillingVersion(totalItems.getFirst().policyVersion());
            return;
        }
        long balance = resolveBalance(tenant.getQuotaBalance());
        String requestId = resolveRequestId(command);
        int eventSeq = command.nextBillingEventSeq();
        BillingChargeResult result = billingLedgerService.reserveAppliedBalance(
                requestId,
                tenantId,
                eventSeq,
                deltaItems,
                balance
        );
        if (!result.charged()) {
            throw new BizException(ErrorCode.BILLING_INVALID, "余额不足，请升级套餐");
        }
        applyReservedUsage(command, deltaUsage);
        command.setReservedCostAmount(totalCost);
        command.setCostAmount(totalCost);
        command.setBillingVersion(totalItems.getFirst().policyVersion());
    }

    /**
     * 对工作流中不经过 SQL 执行器的单次模型调用完成幂等计费。
     *
     * @param tenantId 租户ID
     * @param workflowRequestId 工作流请求ID
     * @param stage 调用阶段
     * @param modelName 模型名称
     * @param tokenCount Token 总量
     * @return 计费结果
     */
    public BillingChargeResult chargeModelUsage(String tenantId,
                                                 String workflowRequestId,
                                                 String stage,
                                                 String modelName,
                                                 Integer tokenCount) {
        if (!StringUtils.hasText(tenantId)
                || !StringUtils.hasText(workflowRequestId)
                || !StringUtils.hasText(stage)
                || tokenCount == null
                || tokenCount <= 0) {
            return BillingChargeResult.skip();
        }
        String invocationKey = tenantId.trim() + ":" + workflowRequestId.trim() + ":" + stage.trim();
        String billingRequestId = UUID.nameUUIDFromBytes(invocationKey.getBytes(StandardCharsets.UTF_8))
                .toString()
                .replace("-", "");
        SqlExecuteLogCommand command = new SqlExecuteLogCommand();
        command.setRequestId(billingRequestId);
        command.setTenantId(tenantId.trim());
        command.setModelName(normalizeModelName(modelName));
        command.setTokenCount(tokenCount);
        command.addModelTokens(modelName, tokenCount);
        reserveCost(tenantId, command);
        return charge(tenantId, command);
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

    private long resolveReservedCost(Long reservedCostAmount) {
        if (reservedCostAmount == null || reservedCostAmount < 0) {
            return 0;
        }
        return reservedCostAmount;
    }

    private Map<String, Integer> resolveReservedUsage(SqlExecuteLogCommand command) {
        Map<String, Integer> usage = new HashMap<>();
        if (command.getReservedModelTokenUsage() != null) {
            command.getReservedModelTokenUsage().forEach((model, tokens) -> {
                if (tokens == null || tokens <= 0) {
                    return;
                }
                usage.put(normalizeModelName(model), tokens);
            });
        }
        return usage;
    }

    private Map<String, Integer> resolveDeltaUsage(Map<String, Integer> tokenUsage,
                                                   Map<String, Integer> reservedUsage) {
        Map<String, Integer> delta = new HashMap<>();
        for (Map.Entry<String, Integer> entry : tokenUsage.entrySet()) {
            int current = entry.getValue() == null ? 0 : entry.getValue();
            int reserved = reservedUsage.getOrDefault(entry.getKey(), 0);
            int diff = current - reserved;
            if (diff > 0) {
                delta.put(entry.getKey(), diff);
            }
        }
        return delta;
    }

    private void applyReservedUsage(SqlExecuteLogCommand command, Map<String, Integer> deltaUsage) {
        if (command == null || deltaUsage == null || deltaUsage.isEmpty()) {
            return;
        }
        deltaUsage.forEach(command::addReservedModelTokens);
    }

    /**
     * 解析模型 Token 使用情况。
     *
     * @param command 日志命令
     * @return 统计结果
     * @author 余炘洋
     * @date 2026-01-29
     */
    private Map<String, Integer> resolveTokenUsage(SqlExecuteLogCommand command) {
        Map<String, Integer> usage = new HashMap<>();
        if (command.getModelTokenUsage() != null) {
            command.getModelTokenUsage().forEach((model, tokens) -> {
                if (tokens == null || tokens <= 0) {
                    return;
                }
                String normalized = normalizeModelName(model);
                usage.merge(normalized, tokens, Integer::sum);
            });
        }
        if (usage.isEmpty() && command.getTokenCount() != null && command.getTokenCount() > 0) {
            String normalized = normalizeModelName(command.getModelName());
            usage.put(normalized, command.getTokenCount());
        }
        return usage;
    }

    /**
     * 构建计费明细。
     *
     * @param tokenUsage  模型 Token 统计
     * @param tenant      租户信息
     * @param override    计费覆盖
     * @return 计费明细
     * @author 余炘洋
     * @date 2026-01-29
     */
    private List<BillingChargeItem> buildChargeItems(Map<String, Integer> tokenUsage,
                                                     TenantInfo tenant,
                                                     TenantBillingOverride override) {
        List<BillingChargeItem> items = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : tokenUsage.entrySet()) {
            if (entry.getValue() == null || entry.getValue() <= 0) {
                continue;
            }
            BillingQuote quote = billingPolicy.quote(
                    entry.getKey(),
                    entry.getValue(),
                    tenant.getTenantType(),
                    override == null ? null : override.getPriceMultiplier()
            );
            items.add(new BillingChargeItem(
                    entry.getKey(),
                    entry.getValue(),
                    quote.unitCost(),
                    quote.costAmount(),
                    quote.policyVersion()
            ));
        }
        return items;
    }

    /**
     * 解析请求ID。
     *
     * @param command 日志命令
     * @return 请求ID
     * @author 余炘洋
     * @date 2026-01-29
     */
    private String resolveRequestId(SqlExecuteLogCommand command) {
        if (command == null) {
            return null;
        }
        if (StringUtils.hasText(command.getRequestId())) {
            return command.getRequestId();
        }
        String fallback = java.util.UUID.randomUUID().toString().replace("-", "");
        command.setRequestId(fallback);
        return fallback;
    }

    /**
     * 规范化模型名称。
     *
     * @param modelName 模型名称
     * @return 规范化名称
     * @author 余炘洋
     * @date 2026-01-29
     */
    private String normalizeModelName(String modelName) {
        if (!StringUtils.hasText(modelName)) {
            return "UNKNOWN";
        }
        return modelName.trim().toUpperCase();
    }
}
