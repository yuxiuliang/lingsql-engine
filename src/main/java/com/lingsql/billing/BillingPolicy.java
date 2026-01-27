package com.lingsql.billing;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Map;

/**
 * 计费策略配置。
 *
 * @author 余炘洋
 * @date 2026-01-27
 */
@Component
public class BillingPolicy {

    /**
     * 默认服务等级。
     *
     * @author 余炘洋
     * @date 2026-01-27
     */
    public static final String DEFAULT_SERVICE_LEVEL = "FREE";

    /**
     * 默认每日调用上限。
     *
     * @author 余炘洋
     * @date 2026-01-27
     */
    private static final int DEFAULT_DAILY_LIMIT = 100;

    /**
     * 默认计费单位点数。
     *
     * @author 余炘洋
     * @date 2026-01-27
     */
    private static final long DEFAULT_UNIT_COST = 1L;

    private static final Map<String, Integer> LEVEL_DAILY_LIMIT = Map.of(
            "FREE", 100,
            "BASIC", 500,
            "PRO", 2000,
            "ENTERPRISE", Integer.MAX_VALUE
    );

    private static final Map<String, Long> MODEL_UNIT_COST = Map.of(
            "GPT-4", 10L,
            "GPT-4O", 8L,
            "GPT-4O-MINI", 4L,
            "QWEN-PLUS", 2L,
            "QWEN-MAX", 4L,
            "DEEPSEEK-CHAT", 2L
    );

    /**
     * 解析每日调用上限。
     *
     * @param serviceLevel 服务等级
     * @return 上限数量
     * @author 余炘洋
     * @date 2026-01-27
     */
    public int resolveDailyLimit(String serviceLevel) {
        String normalized = normalize(serviceLevel);
        if (!StringUtils.hasText(normalized)) {
            return DEFAULT_DAILY_LIMIT;
        }
        return LEVEL_DAILY_LIMIT.getOrDefault(normalized, DEFAULT_DAILY_LIMIT);
    }

    /**
     * 解析模型计费单价。
     *
     * @param modelName 模型名称
     * @return 单价点数
     * @author 余炘洋
     * @date 2026-01-27
     */
    public long resolveUnitCost(String modelName) {
        String normalized = normalize(modelName);
        if (!StringUtils.hasText(normalized)) {
            return DEFAULT_UNIT_COST;
        }
        return MODEL_UNIT_COST.getOrDefault(normalized, DEFAULT_UNIT_COST);
    }

    /**
     * 计算本次调用成本。
     *
     * @param modelName  模型名称
     * @param tokenCount Token 数量
     * @return 成本点数
     * @author 余炘洋
     * @date 2026-01-27
     */
    public long calculateCost(String modelName, Integer tokenCount) {
        long unitCost = resolveUnitCost(modelName);
        long tokens = tokenCount == null || tokenCount <= 0 ? 0 : tokenCount;
        long buckets = tokens == 0 ? 1 : (tokens + 999L) / 1000L;
        return unitCost * Math.max(buckets, 1);
    }

    /**
     * 规范化字符串。
     *
     * @param value 输入
     * @return 规范化结果
     * @author 余炘洋
     * @date 2026-01-27
     */
    private String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim().toUpperCase();
    }
}
