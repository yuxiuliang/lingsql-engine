package com.lingsql.web.model;

import com.lingsql.agent.ModelHealthService;
import com.lingsql.agent.model.AiModelConfig;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 模型健康视图对象。
 *
 * @author 余炘洋
 * @date 2026-01-22
 */
@Data
public class ModelHealthView {

    private Long id;
    private String providerName;
    private String modelName;
    private String baseUrl;
    private Integer isActive;
    private Integer autoRecover;
    private Integer weight;
    private Integer priority;
    private Integer cooldownSeconds;
    private LocalDateTime fuseTime;
    private String fuseReason;

    private int failureCount;
    private boolean fused;
    private String lastFailReason;
    private LocalDateTime lastFailTime;
    private long lastCostMs;

    /**
     * 构建视图对象。
     *
     * @param config   模型配置
     * @param snapshot 健康快照
     * @return 视图对象
     * @author 余炘洋
     * @date 2026-01-22
     */
    public static ModelHealthView from(AiModelConfig config, ModelHealthService.ModelHealthSnapshot snapshot) {
        ModelHealthView view = new ModelHealthView();
        view.setId(config.getId());
        view.setProviderName(config.getProviderName());
        view.setModelName(config.getModelName());
        view.setBaseUrl(config.getBaseUrl());
        view.setIsActive(config.getIsActive());
        view.setAutoRecover(config.getAutoRecover());
        view.setWeight(config.getWeight());
        view.setPriority(config.getPriority());
        view.setCooldownSeconds(config.getCooldownSeconds());
        view.setFuseTime(config.getFuseTime());
        view.setFuseReason(config.getFuseReason());

        if (snapshot != null) {
            view.setFailureCount(snapshot.getFailureCount());
            view.setFused(snapshot.isFused());
            view.setLastFailReason(snapshot.getLastFailReason());
            view.setLastFailTime(snapshot.getLastFailTime());
            view.setLastCostMs(snapshot.getLastCostMs());
        }
        return view;
    }
}
