package com.lingsql.billing;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingsql.billing.entity.BillingPolicyVersion;
import com.lingsql.billing.model.BillingLimit;
import com.lingsql.billing.model.BillingPolicySnapshot;
import com.lingsql.billing.model.BillingQuote;
import com.lingsql.billing.repository.BillingPolicyVersionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 计费策略配置。
 *
 * @author 余炘洋
 * @date 2026-01-27
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BillingPolicy {

    /**
     * 默认服务等级。
     *
     * @author 余炘洋
     * @date 2026-01-27
     */
    public static final String DEFAULT_SERVICE_LEVEL = "FREE";

    private static final int ACTIVE_STATUS = 1;
    private static final int DEFAULT_DAILY_LIMIT = 100;
    private static final long DEFAULT_DAILY_TOKEN_LIMIT = 0L;
    private static final long DEFAULT_UNIT_COST = 1L;
    private static final String DEFAULT_POLICY_VERSION = "DEFAULT";
    private static final BigDecimal DEFAULT_TENANT_WEIGHT = BigDecimal.ONE;
    private static final BigDecimal DEFAULT_OVERRIDE_MULTIPLIER = BigDecimal.ONE;
    private static final int COST_SCALE = 0;
    private static final RoundingMode COST_ROUNDING = RoundingMode.HALF_UP;

    private static final Map<String, Integer> DEFAULT_LEVEL_DAILY_LIMIT = Map.of(
            "FREE", 100,
            "BASIC", 500,
            "PRO", 2000,
            "ENTERPRISE", Integer.MAX_VALUE
    );

    private static final Map<String, Long> DEFAULT_LEVEL_DAILY_TOKEN_LIMIT = Map.of(
            "FREE", 0L,
            "BASIC", 0L,
            "PRO", 0L,
            "ENTERPRISE", Long.MAX_VALUE
    );

    private static final Map<String, Long> DEFAULT_MODEL_UNIT_COST = Map.of(
            "GPT-4", 10L,
            "GPT-4O", 8L,
            "GPT-4O-MINI", 4L,
            "QWEN-PLUS", 2L,
            "QWEN-MAX", 4L,
            "DEEPSEEK-CHAT", 2L
    );

    private static final Map<String, BigDecimal> DEFAULT_TENANT_TYPE_WEIGHT = Collections.emptyMap();

    private static final BillingPolicySnapshot DEFAULT_SNAPSHOT = new BillingPolicySnapshot(
            DEFAULT_POLICY_VERSION,
            DEFAULT_MODEL_UNIT_COST,
            buildDefaultLimitMap(),
            DEFAULT_TENANT_TYPE_WEIGHT,
            DEFAULT_UNIT_COST,
            DEFAULT_DAILY_LIMIT,
            DEFAULT_DAILY_TOKEN_LIMIT,
            DEFAULT_TENANT_WEIGHT
    );

    private final BillingPolicyVersionRepository policyRepository;
    private final ObjectMapper objectMapper;

    private final AtomicReference<BillingPolicySnapshot> cachedSnapshot = new AtomicReference<>();
    private final AtomicReference<String> cachedVersionKey = new AtomicReference<>();

    /**
     * 获取当前生效策略快照。
     *
     * @return 快照
     * @author 余炘洋
     * @date 2026-01-27
     */
    public BillingPolicySnapshot currentSnapshot() {
        LocalDateTime now = LocalDateTime.now();
        BillingPolicyVersion policy = policyRepository
                .findFirstByStatusAndEffectiveTimeLessThanEqualOrderByEffectiveTimeDesc(ACTIVE_STATUS, now)
                .orElseGet(() -> policyRepository.findFirstByStatusOrderByEffectiveTimeDesc(ACTIVE_STATUS).orElse(null));
        if (policy == null) {
            return DEFAULT_SNAPSHOT;
        }
        if (policy.getEffectiveTime() != null && policy.getEffectiveTime().isAfter(now)) {
            return DEFAULT_SNAPSHOT;
        }
        String versionKey = buildVersionKey(policy);
        BillingPolicySnapshot cached = cachedSnapshot.get();
        if (cached != null && Objects.equals(versionKey, cachedVersionKey.get())) {
            return cached;
        }
        BillingPolicySnapshot snapshot = buildSnapshot(policy);
        cachedSnapshot.set(snapshot);
        cachedVersionKey.set(versionKey);
        return snapshot;
    }

    private String buildVersionKey(BillingPolicyVersion policy) {
        if (policy == null) {
            return null;
        }
        return policy.getId() + ":" + policy.getUpdateTime() + ":" + policy.getVersionCode();
    }

    /**
     * 计算本次调用成本。
     *
     * @param modelName        模型名称
     * @param tokenCount       Token 数量
     * @param tenantType       租户类型
     * @param overrideMultiply 覆盖倍率
     * @return 报价结果
     * @author 余炘洋
     * @date 2026-01-27
     */
    public BillingQuote quote(String modelName, Integer tokenCount, String tenantType, BigDecimal overrideMultiply) {
        BillingPolicySnapshot snapshot = currentSnapshot();
        long unitCost = snapshot.resolveUnitCost(modelName);
        long tokens = resolveTokenCount(tokenCount);
        BigDecimal tenantWeight = snapshot.resolveTenantWeight(tenantType);
        BigDecimal multiplier = resolveMultiplier(overrideMultiply);
        BigDecimal totalWeight = tenantWeight.multiply(multiplier);
        BigDecimal amount = BigDecimal.valueOf(unitCost)
                .multiply(BigDecimal.valueOf(tokens))
                .multiply(totalWeight)
                .setScale(COST_SCALE, COST_ROUNDING);
        long costAmount = amount.longValue();
        return BillingQuote.of(costAmount, unitCost, tokens, totalWeight, snapshot.versionCode());
    }

    /**
     * 计算本次调用成本（无权重）。
     *
     * @param modelName  模型名称
     * @param tokenCount Token 数量
     * @return 成本点数
     * @author 余炘洋
     * @date 2026-01-27
     */
    public long calculateCost(String modelName, Integer tokenCount) {
        return quote(modelName, tokenCount, null, DEFAULT_OVERRIDE_MULTIPLIER).costAmount();
    }

    private BillingPolicySnapshot buildSnapshot(BillingPolicyVersion policy) {
        Map<String, Long> unitCost = merge(DEFAULT_MODEL_UNIT_COST, parseUnitCost(policy.getModelUnitCost()));
        Map<String, BillingLimit> limits = mergeLimit(buildDefaultLimitMap(), parseServiceLimits(policy.getServiceLevelLimit()));
        Map<String, BigDecimal> tenantWeight = mergeWeight(DEFAULT_TENANT_TYPE_WEIGHT, parseTenantWeights(policy.getTenantTypeWeight()));
        long defaultUnitCost = resolveLong(policy.getDefaultUnitCost(), DEFAULT_UNIT_COST);
        int defaultDailyLimit = resolveInt(policy.getDefaultDailyLimit(), DEFAULT_DAILY_LIMIT);
        long defaultDailyTokenLimit = resolveLong(policy.getDefaultDailyTokenLimit(), DEFAULT_DAILY_TOKEN_LIMIT);
        BigDecimal defaultWeight = policy.getDefaultTenantWeight() == null ? DEFAULT_TENANT_WEIGHT : policy.getDefaultTenantWeight();
        String versionCode = StringUtils.hasText(policy.getVersionCode()) ? policy.getVersionCode() : DEFAULT_POLICY_VERSION;
        return new BillingPolicySnapshot(versionCode, unitCost, limits, tenantWeight, defaultUnitCost,
                defaultDailyLimit, defaultDailyTokenLimit, defaultWeight);
    }

    private static Map<String, BillingLimit> buildDefaultLimitMap() {
        Map<String, BillingLimit> limitMap = new HashMap<>();
        DEFAULT_LEVEL_DAILY_LIMIT.forEach((level, limit) -> {
            long tokenLimit = DEFAULT_LEVEL_DAILY_TOKEN_LIMIT.getOrDefault(level, DEFAULT_DAILY_TOKEN_LIMIT);
            limitMap.put(level, new BillingLimit(limit, tokenLimit));
        });
        return Collections.unmodifiableMap(limitMap);
    }

    private Map<String, Long> parseUnitCost(String json) {
        if (!StringUtils.hasText(json)) {
            return Collections.emptyMap();
        }
        try {
            Map<String, Long> raw = objectMapper.readValue(json, new TypeReference<>() { });
            return normalizeMap(raw);
        } catch (Exception ex) {
            log.warn("解析模型单价失败: {}", ex.getMessage());
            return Collections.emptyMap();
        }
    }

    private Map<String, BillingLimit> parseServiceLimits(String json) {
        if (!StringUtils.hasText(json)) {
            return Collections.emptyMap();
        }
        try {
            Map<String, BillingLimit> raw = objectMapper.readValue(json, new TypeReference<>() { });
            return normalizeMap(raw);
        } catch (Exception ex) {
            log.warn("解析服务等级限额失败: {}", ex.getMessage());
            return Collections.emptyMap();
        }
    }

    private Map<String, BigDecimal> parseTenantWeights(String json) {
        if (!StringUtils.hasText(json)) {
            return Collections.emptyMap();
        }
        try {
            Map<String, Object> raw = objectMapper.readValue(json, new TypeReference<>() { });
            Map<String, BigDecimal> result = new HashMap<>();
            raw.forEach((key, value) -> {
                BigDecimal weight = parseBigDecimal(value);
                if (weight != null) {
                    result.put(normalize(key), weight);
                }
            });
            return result;
        } catch (Exception ex) {
            log.warn("解析租户类型权重失败: {}", ex.getMessage());
            return Collections.emptyMap();
        }
    }

    private Map<String, Long> merge(Map<String, Long> base, Map<String, Long> override) {
        if (override == null || override.isEmpty()) {
            return base;
        }
        Map<String, Long> merged = new HashMap<>(base);
        merged.putAll(override);
        return Collections.unmodifiableMap(merged);
    }

    private Map<String, BillingLimit> mergeLimit(Map<String, BillingLimit> base, Map<String, BillingLimit> override) {
        if (override == null || override.isEmpty()) {
            return base;
        }
        Map<String, BillingLimit> merged = new HashMap<>(base);
        merged.putAll(override);
        return Collections.unmodifiableMap(merged);
    }

    private Map<String, BigDecimal> mergeWeight(Map<String, BigDecimal> base, Map<String, BigDecimal> override) {
        if (override == null || override.isEmpty()) {
            return base;
        }
        Map<String, BigDecimal> merged = new HashMap<>(base);
        merged.putAll(override);
        return Collections.unmodifiableMap(merged);
    }

    private <T> Map<String, T> normalizeMap(Map<String, T> input) {
        if (input == null || input.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, T> normalized = new HashMap<>();
        input.forEach((key, value) -> {
            String normalizedKey = normalize(key);
            if (StringUtils.hasText(normalizedKey)) {
                normalized.put(normalizedKey, value);
            }
        });
        return Collections.unmodifiableMap(normalized);
    }

    private BigDecimal parseBigDecimal(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal bigDecimal) {
            return bigDecimal;
        }
        if (value instanceof Number number) {
            return new BigDecimal(number.toString());
        }
        if (value instanceof String text && StringUtils.hasText(text)) {
            try {
                return new BigDecimal(text.trim());
            } catch (NumberFormatException ex) {
                return null;
            }
        }
        return null;
    }

    private BigDecimal resolveMultiplier(BigDecimal overrideMultiply) {
        if (overrideMultiply == null || overrideMultiply.compareTo(BigDecimal.ZERO) <= 0) {
            return DEFAULT_OVERRIDE_MULTIPLIER;
        }
        return overrideMultiply;
    }

    private long resolveTokenCount(Integer tokenCount) {
        if (tokenCount == null || tokenCount <= 0) {
            return 0L;
        }
        return tokenCount;
    }

    private long resolveLong(Long value, long fallback) {
        if (value == null || value < 0) {
            return fallback;
        }
        return value;
    }

    private int resolveInt(Integer value, int fallback) {
        if (value == null || value < 0) {
            return fallback;
        }
        return value;
    }

    private String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim().toUpperCase();
    }
}
