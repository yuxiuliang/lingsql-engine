package com.lingsql.agent;

import com.lingsql.agent.model.AiModelConfig;
import com.lingsql.agent.repository.AiModelConfigRepository;
import com.lingsql.common.exception.BizException;
import com.lingsql.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 模型配置服务，确保配置变更后重新进入待验证状态。
 *
 * @author 余炘洋
 * @date 2026-08-15
 * @modify 余炘洋 2026-08-16 禁止删除仍被租户授权引用的模型
 */
@Service
@RequiredArgsConstructor
public class ModelConfigService {

    private final AiModelConfigRepository repository;
    private final ModelHealthService healthService;
    private final TenantModelRouting tenantModelRouting;

    public List<AiModelConfig> list() {
        return repository.findAll();
    }

    @Transactional
    public AiModelConfig create(AiModelConfig request) {
        request.setId(null);
        markPendingVerification(request);
        return repository.save(request);
    }

    @Transactional
    public AiModelConfig update(Long id, AiModelConfig request) {
        AiModelConfig config = repository.findById(id)
                .orElseThrow(() -> new BizException(ErrorCode.MODEL_NOT_FOUND, "模型配置不存在"));
        config.setProviderName(request.getProviderName());
        config.setModelName(request.getModelName());
        config.setModelVersion(request.getModelVersion());
        config.setBaseUrl(request.getBaseUrl());
        if (StringUtils.hasText(request.getApiKey())) {
            config.setApiKey(request.getApiKey());
        }
        config.setWeight(request.getWeight());
        config.setPriority(request.getPriority());
        config.setAutoRecover(request.getAutoRecover());
        config.setCooldownSeconds(request.getCooldownSeconds());
        markPendingVerification(config);
        AiModelConfig saved = repository.save(config);
        healthService.resetHealth(id);
        return saved;
    }

    @Transactional
    public void delete(Long id) {
        if (tenantModelRouting.hasRouteForModel(id)) {
            throw new BizException(ErrorCode.CONFIG_INVALID, "模型仍被租户授权路由引用，禁止删除");
        }
        repository.deleteById(id);
        healthService.resetHealth(id);
    }

    private void markPendingVerification(AiModelConfig config) {
        config.setIsActive(0);
        config.setFuseTime(null);
        config.setFuseReason("待验证");
        config.setLastProbeStatus(null);
        config.setLastProbeTime(null);
        config.setLastProbeCostMs(null);
        config.setLastProbeErrorCode(null);
    }
}
