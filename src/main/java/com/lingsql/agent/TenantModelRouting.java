package com.lingsql.agent;

import com.lingsql.agent.model.AiModelConfig;
import com.lingsql.agent.model.TenantModelRoute;
import com.lingsql.agent.model.TenantModelRouteCommand;
import com.lingsql.agent.model.TenantModelRouteView;
import com.lingsql.agent.repository.AiModelConfigRepository;
import com.lingsql.agent.repository.TenantModelRouteRepository;
import com.lingsql.common.exception.BizException;
import com.lingsql.common.exception.ErrorCode;
import com.lingsql.tenant.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 租户模型路由深模块，统一维护授权并生成不可变调用计划。
 *
 * @author 余炘洋
 * @date 2026-08-16
 */
@Service
@RequiredArgsConstructor
public class TenantModelRouting {

    private static final int ACTIVE = 1;
    private static final int NOT_DELETED = 0;
    private static final int MAX_ROUTES_PER_TENANT = 16;

    private final TenantModelRouteRepository routeRepository;
    private final AiModelConfigRepository modelRepository;
    private final TenantRepository tenantRepository;
    private final ModelRouteFingerprintCalculator fingerprintCalculator;

    /**
     * 冻结租户当前可调度的模型路由。没有显式授权时拒绝调用，不回退到全局模型池。
     *
     * @param tenantId 租户标识
     * @return 不可变路由计划
     */
    @Transactional(readOnly = true)
    public ModelRoutePlan planFor(String tenantId) {
        String normalizedTenantId = requireTenantId(tenantId);
        List<TenantModelRoute> routes = routeRepository
                .findByTenantIdAndStatusOrderByRoutePriorityAscIdAsc(normalizedTenantId, ACTIVE);
        if (routes.isEmpty()) {
            throw new BizException(ErrorCode.MODEL_UNAVAILABLE, "当前租户未配置模型授权路由");
        }

        Map<Long, AiModelConfig> models = loadModels(routes);
        if (models.size() != routes.stream().map(TenantModelRoute::getModelConfigId).distinct().count()) {
            throw new BizException(ErrorCode.CONFIG_INVALID, "租户模型授权引用了不存在的物理模型");
        }
        List<ModelRouteCandidate> candidates = routes.stream()
                .map(route -> toCandidate(route, models.get(route.getModelConfigId())))
                .filter(candidate -> candidate.modelConfig() != null)
                .filter(candidate -> Integer.valueOf(ACTIVE).equals(candidate.modelConfig().getIsActive()))
                .filter(candidate -> "HEALTHY".equals(candidate.modelConfig().getLastProbeStatus()))
                .toList();
        if (candidates.isEmpty()) {
            throw new BizException(ErrorCode.MODEL_UNAVAILABLE, "当前租户没有已启用且验证健康的授权模型");
        }
        return new ModelRoutePlan(
                normalizedTenantId,
                fingerprintCalculator.calculate(normalizedTenantId, candidates),
                candidates
        );
    }

    /**
     * 原子替换一个租户的全部模型授权。空列表会被拒绝，避免活跃租户意外失去模型能力。
     *
     * @param tenantId 租户标识
     * @param commands 新授权集合
     * @return 生效后的安全视图
     */
    @Transactional
    public List<TenantModelRouteView> replace(String tenantId, List<TenantModelRouteCommand> commands) {
        String normalizedTenantId = requireTenantId(tenantId);
        tenantRepository.findByTenantIdAndIsDeletedForUpdate(normalizedTenantId, NOT_DELETED)
                .orElseThrow(() -> new BizException(ErrorCode.INVALID_PARAM, "租户不存在"));
        validateCommands(commands);
        Map<Long, AiModelConfig> models = loadRequestedModels(commands);

        routeRepository.deleteByTenantId(normalizedTenantId);
        List<TenantModelRoute> saved = commands.stream()
                .map(command -> toEntity(normalizedTenantId, command))
                .map(routeRepository::save)
                .toList();
        return saved.stream().map(route -> toView(route, models.get(route.getModelConfigId()))).toList();
    }

    @Transactional(readOnly = true)
    public List<TenantModelRouteView> list(String tenantId) {
        String normalizedTenantId = requireTenantId(tenantId);
        List<TenantModelRoute> routes = routeRepository.findByTenantIdOrderByRoutePriorityAscIdAsc(normalizedTenantId);
        Map<Long, AiModelConfig> models = loadModels(routes);
        return routes.stream().map(route -> toView(route, models.get(route.getModelConfigId()))).toList();
    }

    @Transactional(readOnly = true)
    public boolean hasRouteForModel(Long modelConfigId) {
        return modelConfigId != null && routeRepository.existsByModelConfigId(modelConfigId);
    }

    private Map<Long, AiModelConfig> loadModels(List<TenantModelRoute> routes) {
        List<Long> ids = routes.stream().map(TenantModelRoute::getModelConfigId).distinct().toList();
        Map<Long, AiModelConfig> result = new LinkedHashMap<>();
        modelRepository.findAllById(ids).forEach(model -> result.put(model.getId(), model));
        return result;
    }

    private Map<Long, AiModelConfig> loadRequestedModels(List<TenantModelRouteCommand> commands) {
        List<Long> ids = commands.stream().map(TenantModelRouteCommand::modelConfigId).distinct().toList();
        Map<Long, AiModelConfig> models = new LinkedHashMap<>();
        modelRepository.findAllById(ids).forEach(model -> models.put(model.getId(), model));
        if (models.size() != ids.size()) {
            throw new BizException(ErrorCode.MODEL_NOT_FOUND, "授权路由包含不存在的模型");
        }
        boolean invalid = models.values().stream().anyMatch(model ->
                !Integer.valueOf(ACTIVE).equals(model.getIsActive())
                        || !"HEALTHY".equals(model.getLastProbeStatus()));
        if (invalid) {
            throw new BizException(ErrorCode.CONFIG_INVALID, "只有已启用且验证健康的模型可以授权给租户");
        }
        return models;
    }

    private void validateCommands(List<TenantModelRouteCommand> commands) {
        if (commands == null || commands.isEmpty() || commands.size() > MAX_ROUTES_PER_TENANT) {
            throw new BizException(ErrorCode.INVALID_PARAM, "模型授权数量必须为 1-16 个");
        }
        Set<Long> modelIds = new HashSet<>();
        for (TenantModelRouteCommand command : commands) {
            if (command == null || command.modelConfigId() == null
                    || command.priority() == null || command.priority() < 1 || command.priority() > 100
                    || command.weight() == null || command.weight() < 1 || command.weight() > 1000) {
                throw new BizException(ErrorCode.INVALID_PARAM, "模型授权参数不合法");
            }
            if (!modelIds.add(command.modelConfigId())) {
                throw new BizException(ErrorCode.INVALID_PARAM, "同一模型不能重复授权");
            }
        }
    }

    private String requireTenantId(String tenantId) {
        if (!StringUtils.hasText(tenantId)
                || !tenantId.trim().matches("[A-Za-z0-9][A-Za-z0-9_-]{0,63}")) {
            throw new BizException(ErrorCode.INVALID_PARAM, "租户标识不合法");
        }
        return tenantId.trim();
    }

    private ModelRouteCandidate toCandidate(TenantModelRoute route, AiModelConfig model) {
        return new ModelRouteCandidate(model, route.getRoutePriority(), route.getRouteWeight());
    }

    private TenantModelRoute toEntity(String tenantId, TenantModelRouteCommand command) {
        TenantModelRoute route = new TenantModelRoute();
        route.setTenantId(tenantId);
        route.setModelConfigId(command.modelConfigId());
        route.setRoutePriority(command.priority());
        route.setRouteWeight(command.weight());
        route.setStatus(ACTIVE);
        return route;
    }

    private TenantModelRouteView toView(TenantModelRoute route, AiModelConfig model) {
        return new TenantModelRouteView(
                route.getId(), route.getTenantId(), route.getModelConfigId(),
                model == null ? null : model.getProviderName(),
                model == null ? null : model.getModelName(),
                model == null ? null : model.getModelVersion(),
                route.getRoutePriority(), route.getRouteWeight(), route.getStatus()
        );
    }
}
