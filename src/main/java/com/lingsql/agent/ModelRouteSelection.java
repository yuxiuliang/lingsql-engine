package com.lingsql.agent;

import com.lingsql.agent.model.AiModelConfig;

/**
 * 一次模型路由选择及其配置快照指纹。
 *
 * @param modelConfig 实际选中的模型配置
 * @param routeFingerprint 选择时全部启用模型配置的路由指纹
 * @author 余炘洋
 * @date 2026-08-15
 */
public record ModelRouteSelection(
        AiModelConfig modelConfig,
        String routeFingerprint
) {
}
