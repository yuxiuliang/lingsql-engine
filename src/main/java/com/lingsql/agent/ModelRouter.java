package com.lingsql.agent;

import com.lingsql.common.exception.BizException;
import com.lingsql.common.exception.ErrorCode;
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
 * @modify 余炘洋 2026-08-16 改为租户显式授权的冻结路由计划
 */
@Component
@RequiredArgsConstructor
public class ModelRouter {

    private final TenantModelRouting tenantModelRouting;
    private final ModelHealthService healthService;
    private final Random random = new Random();

    /**
     * 从数据库中选择可用模型，支持优先级与权重负载均衡。
     *
     * @param tenantId 租户标识
     * @return 冻结路由计划
     * @author 余炘洋
     * @date 2026-01-22
     */
    public ModelRoutePlan planFor(String tenantId) {
        return tenantModelRouting.planFor(tenantId);
    }

    /**
     * 从已冻结的租户授权计划中选择模型。主备调用复用同一计划，避免授权变更或并发查询造成漂移。
     *
     * @param plan 冻结路由计划
     * @param excludeIds 需要排除的模型 ID
     * @return 带路由证据的选择结果
     */
    public ModelRouteSelection selectFromPlan(ModelRoutePlan plan, List<Long> excludeIds) {
        List<Long> excludes = excludeIds == null ? List.of() : excludeIds;
        List<ModelRouteCandidate> available = plan.candidates().stream()
                .filter(candidate -> candidate.modelConfig().getId() != null)
                .filter(candidate -> !excludes.contains(candidate.modelConfig().getId()))
                .filter(candidate -> healthService.isAvailable(candidate.modelConfig()))
                .collect(Collectors.toList());

        if (available.isEmpty()) {
            throw new BizException(ErrorCode.MODEL_UNAVAILABLE, "当前没有可用模型（可能已熔断或被排除）。");
        }

        int minPriority = available.stream()
                .map(ModelRouteCandidate::priority)
                .min(Comparator.naturalOrder())
                .orElse(1);

        List<ModelRouteCandidate> candidates = available.stream()
                .filter(candidate -> candidate.priority() == minPriority)
                .collect(Collectors.toList());

        return new ModelRouteSelection(weightedRandom(candidates).modelConfig(), plan.routeFingerprint());
    }

    /**
     * 权重随机选择。
     *
     * @param configs 候选模型
     * @return 选中的模型
     * @author 余炘洋
     * @date 2026-01-22
     */
    private ModelRouteCandidate weightedRandom(List<ModelRouteCandidate> configs) {
        int totalWeight = configs.stream()
                .mapToInt(candidate -> healthService.resolveWeight(candidate.modelConfig(), candidate.weight()))
                .sum();
        int hitWeight = random.nextInt(Math.max(totalWeight, 1));

        ModelRouteCandidate selected = configs.get(0);
        int currentWeight = 0;
        for (ModelRouteCandidate candidate : configs) {
            int weight = healthService.resolveWeight(candidate.modelConfig(), candidate.weight());
            currentWeight += weight;
            if (hitWeight < currentWeight) {
                selected = candidate;
                break;
            }
        }
        return selected;
    }
}
