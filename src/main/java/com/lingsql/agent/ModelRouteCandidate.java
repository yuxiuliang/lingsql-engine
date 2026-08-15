package com.lingsql.agent;

import com.lingsql.agent.model.AiModelConfig;

/**
 * 冻结在一次租户路由计划中的模型候选。
 *
 * @param modelConfig 物理模型配置
 * @param priority 租户内优先级
 * @param weight 租户内权重
 * @author 余炘洋
 * @date 2026-08-16
 */
public record ModelRouteCandidate(AiModelConfig modelConfig, int priority, int weight) {
}
