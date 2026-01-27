package com.lingsql.billing.model;

import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 计费策略快照。
 *
 * @author 余炘洋
 * @date 2026-01-27
 */
public record BillingPolicySnapshot(String versionCode,
                                   Map<String, Long> modelUnitCost,
                                   Map<String, BillingLimit> serviceLevelLimit,
                                   Map<String, BigDecimal> tenantTypeWeight,
                                   long defaultUnitCost,
                                   int defaultDailyLimit,
                                   long defaultDailyTokenLimit,
                                   BigDecimal defaultTenantWeight) {

    /**
     * 解析模型单价。
     *
     * @param modelName 模型名称
     * @return 单价
     * @author 余炘洋
     * @date 2026-01-27
     */
    public long resolveUnitCost(String modelName) {
        String normalized = normalize(modelName);
        if (!StringUtils.hasText(normalized)) {
            return defaultUnitCost;
        }
        return modelUnitCost.getOrDefault(normalized, defaultUnitCost);
    }

    /**
     * 解析服务等级限额。
     *
     * @param serviceLevel 服务等级
     * @return 限额
     * @author 余炘洋
     * @date 2026-01-27
     */
    public BillingLimit resolveLimit(String serviceLevel) {
        String normalized = normalize(serviceLevel);
        if (!StringUtils.hasText(normalized)) {
            return new BillingLimit(defaultDailyLimit, defaultDailyTokenLimit);
        }
        BillingLimit limit = serviceLevelLimit.get(normalized);
        if (limit == null) {
            return new BillingLimit(defaultDailyLimit, defaultDailyTokenLimit);
        }
        int dailyCallLimit = limit.getDailyCallLimit() < 0 ? defaultDailyLimit : limit.getDailyCallLimit();
        long dailyTokenLimit = limit.getDailyTokenLimit() < 0 ? defaultDailyTokenLimit : limit.getDailyTokenLimit();
        return new BillingLimit(dailyCallLimit, dailyTokenLimit);
    }

    /**
     * 解析租户类型权重。
     *
     * @param tenantType 租户类型
     * @return 权重
     * @author 余炘洋
     * @date 2026-01-27
     */
    public BigDecimal resolveTenantWeight(String tenantType) {
        String normalized = normalize(tenantType);
        if (!StringUtils.hasText(normalized)) {
            return defaultTenantWeight;
        }
        BigDecimal weight = tenantTypeWeight.get(normalized);
        return weight == null ? defaultTenantWeight : weight;
    }

    private String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim().toUpperCase();
    }
}
