package com.lingsql.agent;

import com.lingsql.agent.model.AiModelConfig;
import com.lingsql.agent.repository.AiModelConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

/**
 * 模型路由器，从数据库中选择可用模型做负载均衡。
 *
 * @author 余炘洋
 * @date 2026-01-22
 */
@Component
@RequiredArgsConstructor
public class ModelRouter {

    private final AiModelConfigRepository repository;
    private final ModelHealthService healthService;
    private final Random random = new Random();

    /**
     * 从数据库中选择可用模型，支持优先级与权重负载均衡。
     *
     * @return 模型配置
     * @author 余炘洋
     * @date 2026-01-22
     */
    public AiModelConfig selectModel() {
        return selectModel(List.of());
    }

    /**
     * 从数据库中选择可用模型，支持排除指定模型。
     *
     * @param excludeIds 需要排除的模型ID
     * @return 模型配置
     * @author 余炘洋
     * @date 2026-01-22
     */
    public AiModelConfig selectModel(List<Long> excludeIds) {
        List<AiModelConfig> configs = repository.findByIsActive(1);
        if (configs.isEmpty()) {
            throw new RuntimeException("没有可用的 AI 模型配置，请检查数据库。");
        }

        List<Long> excludes = excludeIds == null ? List.of() : excludeIds;
        List<AiModelConfig> available = configs.stream()
                .filter(config -> config.getId() != null)
                .filter(config -> !excludes.contains(config.getId()))
                .filter(healthService::isAvailable)
                .collect(Collectors.toList());

        if (available.isEmpty()) {
            throw new RuntimeException("当前没有可用模型（可能已熔断或被排除）。");
        }

        int minPriority = available.stream()
                .map(AiModelConfig::getPriority)
                .filter(value -> value != null)
                .min(Comparator.naturalOrder())
                .orElse(1);

        List<AiModelConfig> candidates = available.stream()
                .filter(config -> config.getPriority() == null || config.getPriority() == minPriority)
                .collect(Collectors.toList());

        return weightedRandom(candidates);
    }

    /**
     * 权重随机选择。
     *
     * @param configs 候选模型
     * @return 选中的模型
     * @author 余炘洋
     * @date 2026-01-22
     */
    private AiModelConfig weightedRandom(List<AiModelConfig> configs) {
        int totalWeight = configs.stream()
                .mapToInt(healthService::resolveWeight)
                .sum();
        int hitWeight = random.nextInt(Math.max(totalWeight, 1));

        AiModelConfig selected = configs.get(0);
        int currentWeight = 0;
        for (AiModelConfig config : configs) {
            int weight = healthService.resolveWeight(config);
            currentWeight += weight;
            if (hitWeight < currentWeight) {
                selected = config;
                break;
            }
        }
        return selected;
    }
}
