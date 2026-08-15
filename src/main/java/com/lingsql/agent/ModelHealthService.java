package com.lingsql.agent;

import com.lingsql.agent.model.AiModelConfig;
import com.lingsql.agent.repository.AiModelConfigRepository;
import com.lingsql.common.exception.BizException;
import com.lingsql.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 模型健康状态管理，负责降权与熔断控制。
 *
 * @author 余炘洋
 * @date 2026-01-22
 * @modify 余炘洋 2026-08-16 支持租户路由权重降权
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ModelHealthService {

    private static final int DEFAULT_WEIGHT = 10;
    private static final int FAILURE_THRESHOLD = 3;

    private final AiModelConfigRepository repository;

    /**
     * 模型健康状态缓存（内存态）。
     *
     * @author 余炘洋
     * @date 2026-01-22
     */
    private final Map<Long, ModelHealth> healthMap = new ConcurrentHashMap<>();

    /**
     * 判断模型是否可用（未熔断）。
     *
     * @param config 模型配置
     * @return 是否可用
     * @author 余炘洋
     * @date 2026-01-22
     */
    public boolean isAvailable(AiModelConfig config) {
        if (config == null || config.getId() == null) {
            return false;
        }
        ModelHealth health = healthMap.get(config.getId());
        return health == null || !health.isFused();
    }

    /**
     * 获取模型的有效权重（根据失败次数降权）。
     *
     * @param config 模型配置
     * @return 有效权重
     * @author 余炘洋
     * @date 2026-01-22
     */
    public int resolveWeight(AiModelConfig config) {
        if (config == null || config.getId() == null) {
            return 0;
        }
        int baseWeight = config.getWeight() == null ? DEFAULT_WEIGHT : config.getWeight();
        return resolveWeight(config, baseWeight);
    }

    /**
     * 按租户路由权重计算模型的当前有效权重。
     *
     * @param config 模型配置
     * @param baseWeight 租户内基础权重
     * @return 熔断和失败降权后的权重
     */
    public int resolveWeight(AiModelConfig config, int baseWeight) {
        if (config == null || config.getId() == null || baseWeight <= 0) {
            return 0;
        }
        ModelHealth health = healthMap.get(config.getId());
        if (health == null) {
            return baseWeight;
        }
        if (health.isFused()) {
            return 0;
        }
        int failCount = health.getFailureCount();
        if (failCount <= 0) {
            return baseWeight;
        }
        return Math.max(1, baseWeight / (failCount + 1));
    }

    /**
     * 记录模型调用成功。
     *
     * @param config 模型配置
     * @param costMs 耗时
     * @author 余炘洋
     * @date 2026-01-22
     */
    public void recordSuccess(AiModelConfig config, long costMs) {
        if (config == null || config.getId() == null) {
            return;
        }
        ModelHealth health = healthMap.computeIfAbsent(config.getId(), key -> new ModelHealth());
        health.reset(costMs);
    }

    /**
     * 记录模型调用失败，达到阈值时触发熔断。
     *
     * @param config 模型配置
     * @param reason 失败原因
     * @author 余炘洋
     * @date 2026-01-22
     */
    public void recordFailure(AiModelConfig config, String reason) {
        if (config == null || config.getId() == null) {
            return;
        }
        String finalReason = StringUtils.hasText(reason) ? reason : "未知失败";
        ModelHealth health = healthMap.computeIfAbsent(config.getId(), key -> new ModelHealth());
        int failCount = health.recordFailure(finalReason);
        log.warn("模型调用失败: id={}, provider={}, model={}, failCount={}, reason={}",
                config.getId(),
                config.getProviderName(),
                config.getModelName(),
                failCount,
                finalReason);

        if (failCount >= FAILURE_THRESHOLD) {
            fuseModel(config, "失败次数达到阈值: " + failCount);
        }
    }

    /**
     * 熔断模型并写入数据库（需人工恢复）。
     *
     * @param config 模型配置
     * @param reason 熔断原因
     * @author 余炘洋
     * @date 2026-01-22
     */
    @Transactional
    public void fuseModel(AiModelConfig config, String reason) {
        if (config == null || config.getId() == null) {
            return;
        }
        ModelHealth health = healthMap.computeIfAbsent(config.getId(), key -> new ModelHealth());
        if (health.isFused()) {
            return;
        }
        config.setIsActive(0);
        config.setFuseTime(LocalDateTime.now());
        config.setFuseReason(reason);
        repository.save(config);
        health.markFused(reason);
        log.error("模型已熔断: id={}, provider={}, model={}, reason={}",
                config.getId(),
                config.getProviderName(),
                config.getModelName(),
                reason);
    }

    /**
     * 手动启用模型并清空健康状态。
     *
     * @param modelId 模型ID
     * @author 余炘洋
     * @date 2026-01-22
     */
    @Transactional
    public void enableModel(Long modelId) {
        AiModelConfig config = repository.findById(modelId)
                .orElseThrow(() -> new BizException(ErrorCode.MODEL_NOT_FOUND, "模型不存在"));
        if (!"HEALTHY".equals(config.getLastProbeStatus())) {
            throw new BizException(ErrorCode.CONFIG_INVALID, "模型尚未通过验证，禁止启用");
        }
        config.setIsActive(1);
        config.setFuseTime(null);
        config.setFuseReason(null);
        repository.save(config);
        resetHealth(modelId);
    }

    /**
     * 手动禁用模型并清空健康状态。
     *
     * @param modelId 模型ID
     * @author 余炘洋
     * @date 2026-01-22
     */
    @Transactional
    public void disableModel(Long modelId) {
        AiModelConfig config = repository.findById(modelId)
                .orElseThrow(() -> new BizException(ErrorCode.MODEL_NOT_FOUND, "模型不存在"));
        config.setIsActive(0);
        config.setFuseTime(null);
        config.setFuseReason("人工禁用");
        repository.save(config);
        resetHealth(modelId);
    }

    /**
     * 清理模型健康状态。
     *
     * @param modelId 模型ID
     * @author 余炘洋
     * @date 2026-01-22
     */
    public void resetHealth(Long modelId) {
        if (modelId == null) {
            return;
        }
        healthMap.remove(modelId);
    }

    /**
     * 获取模型健康快照。
     *
     * @param modelId 模型ID
     * @return 健康快照
     * @author 余炘洋
     * @date 2026-01-22
     */
    public ModelHealthSnapshot getSnapshot(Long modelId) {
        if (modelId == null) {
            return ModelHealthSnapshot.empty();
        }
        ModelHealth health = healthMap.get(modelId);
        if (health == null) {
            return ModelHealthSnapshot.empty();
        }
        return new ModelHealthSnapshot(
                true,
                health.getFailureCount(),
                health.isFused(),
                health.getLastFailReason(),
                health.getLastFailTime(),
                health.getLastCostMs());
    }

    /**
     * 健康快照对象。
     *
     * @author 余炘洋
     * @date 2026-01-22
     */
    public static class ModelHealthSnapshot {
        private final boolean tested;
        private final int failureCount;
        private final boolean fused;
        private final String lastFailReason;
        private final LocalDateTime lastFailTime;
        private final long lastCostMs;

        public ModelHealthSnapshot(boolean tested,
                                   int failureCount,
                                   boolean fused,
                                   String lastFailReason,
                                   LocalDateTime lastFailTime,
                                   long lastCostMs) {
            this.tested = tested;
            this.failureCount = failureCount;
            this.fused = fused;
            this.lastFailReason = lastFailReason;
            this.lastFailTime = lastFailTime;
            this.lastCostMs = lastCostMs;
        }

        public static ModelHealthSnapshot empty() {
            return new ModelHealthSnapshot(false, 0, false, null, null, 0L);
        }

        public boolean isTested() {
            return tested;
        }

        public int getFailureCount() {
            return failureCount;
        }

        public boolean isFused() {
            return fused;
        }

        public String getLastFailReason() {
            return lastFailReason;
        }

        public LocalDateTime getLastFailTime() {
            return lastFailTime;
        }

        public long getLastCostMs() {
            return lastCostMs;
        }
    }

    /**
     * 模型健康快照。
     *
     * @author 余炘洋
     * @date 2026-01-22
     */
    private static class ModelHealth {
        private int failureCount;
        private boolean fused;
        private String lastFailReason;
        private LocalDateTime lastFailTime;
        private long lastCostMs;

        private int recordFailure(String reason) {
            this.failureCount++;
            this.lastFailReason = reason;
            this.lastFailTime = LocalDateTime.now();
            return this.failureCount;
        }

        private void reset(long costMs) {
            this.failureCount = 0;
            this.fused = false;
            this.lastFailReason = null;
            this.lastFailTime = null;
            this.lastCostMs = costMs;
        }

        private void markFused(String reason) {
            this.fused = true;
            this.lastFailReason = reason;
            this.lastFailTime = LocalDateTime.now();
        }

        private int getFailureCount() {
            return failureCount;
        }

        private boolean isFused() {
            return fused;
        }

        private String getLastFailReason() {
            return lastFailReason;
        }

        private LocalDateTime getLastFailTime() {
            return lastFailTime;
        }

        private long getLastCostMs() {
            return lastCostMs;
        }
    }
}
