package com.lingsql.agent;

import com.lingsql.agent.model.AiModelConfig;
import com.lingsql.agent.repository.AiModelConfigRepository;
import com.lingsql.config.LingSqlProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 模型自动恢复调度器，自动尝试恢复已熔断模型。
 *
 * @author 余炘洋
 * @date 2026-01-22
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ModelRecoveryScheduler {

    private static final int DEFAULT_COOLDOWN_SECONDS = 600;

    private final AiModelConfigRepository repository;
    private final ModelHealthService healthService;
    private final LingSqlProperties properties;
    private final ModelProbeClient probeClient;

    /**
     * 定时检查已熔断模型并尝试自动恢复。
     *
     * @author 余炘洋
     * @date 2026-01-22
     */
    @Scheduled(fixedDelay = 60000)
    public void autoRecover() {
        List<AiModelConfig> fusedModels = repository.findByIsActiveAndAutoRecover(0, 1);
        if (fusedModels.isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        for (AiModelConfig config : fusedModels) {
            if (config.getFuseTime() == null) {
                continue;
            }
            Duration cooldown = resolveCooldown(config);
            Duration elapsed = Duration.between(config.getFuseTime(), now);
            if (elapsed.compareTo(cooldown) < 0) {
                continue;
            }
            ModelProbeClient.ProbeResult probeResult = probeClient.probe(config);
            if (!probeResult.isSuccess()) {
                config.setFuseTime(now);
                config.setFuseReason("半开探测失败: " + probeResult.getReason());
                repository.save(config);
                log.warn("模型半开探测失败，继续熔断: id={}, provider={}, model={}, reason={}",
                        config.getId(),
                        config.getProviderName(),
                        config.getModelName(),
                        probeResult.getReason());
                continue;
            }

            config.setIsActive(1);
            config.setFuseTime(null);
            config.setFuseReason(null);
            repository.save(config);
            healthService.resetHealth(config.getId());
            log.info("模型自动恢复成功: id={}, provider={}, model={}",
                    config.getId(),
                    config.getProviderName(),
                    config.getModelName());
        }
    }

    /**
     * 解析冷却时间。
     *
     * @param config 模型配置
     * @return 冷却时间
     * @author 余炘洋
     * @date 2026-01-22
     */
    private Duration resolveCooldown(AiModelConfig config) {
        if (config.getCooldownSeconds() != null && config.getCooldownSeconds() > 0) {
            return Duration.ofSeconds(config.getCooldownSeconds());
        }
        if (properties.getModel() != null && properties.getModel().getCooldown() != null) {
            return properties.getModel().getCooldown();
        }
        return Duration.ofSeconds(DEFAULT_COOLDOWN_SECONDS);
    }
}
