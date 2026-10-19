package com.lingsql.agent.tool;

import com.lingsql.agent.knowledge.KnowledgeReferenceRenderer;
import com.lingsql.agent.orchestration.AgentCallerType;
import com.lingsql.agent.orchestration.AgentExecutionContext;
import com.lingsql.agent.orchestration.AgentRunControl;
import com.lingsql.agent.orchestration.AgentRunPolicy;
import com.lingsql.agent.plugin.AgentPluginExecutor;
import com.lingsql.agent.plugin.CalculateMetricPlugin;
import com.lingsql.agent.plugin.KnowledgeRetrievePlugin;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.UUID;

/**
 * 声明式工具提供者：把内置插件暴露为 Spring AI ToolCallback。
 *
 * <p>调用统一经过 {@link AgentPluginExecutor} 网关，因此同样受策略白名单、租户插件授权、
 * 预算、截止时间与调用 Outbox 约束；未携带租户上下文时直接拒绝。</p>
 *
 * @author 余炘洋
 * @date 2026-10-05
 */
@Service
public class AgentToolProvider {

    // 说明：图表映射需要完整受限结果集，无法作为无参工具暴露，必须经插件网关直接使用 chart_mapper。

    private final AgentPluginExecutor pluginExecutor;

    public AgentToolProvider(AgentPluginExecutor pluginExecutor) {
        this.pluginExecutor = pluginExecutor;
    }

    @Tool(description = "执行受控数值表达式指标计算")
    public String calculateMetric(String tenantId, String expression) {
        CalculateMetricPlugin.Result result = pluginExecutor.execute(
                CalculateMetricPlugin.PLUGIN_ID,
                toolContext(tenantId, CalculateMetricPlugin.PLUGIN_ID),
                toolControl(CalculateMetricPlugin.PLUGIN_ID),
                new CalculateMetricPlugin.Request(expression, java.util.Map.of()),
                CalculateMetricPlugin.Result.class
        );
        return String.valueOf(result.value());
    }

    @Tool(description = "受控知识检索（租户/权限/版本过滤，返回带来源与版本的参考数据）")
    public String knowledgeRetrieve(String tenantId, String query, int maxResults) {
        KnowledgeRetrievePlugin.Result result = pluginExecutor.execute(
                KnowledgeRetrievePlugin.PLUGIN_ID,
                toolContext(tenantId, KnowledgeRetrievePlugin.PLUGIN_ID),
                toolControl(KnowledgeRetrievePlugin.PLUGIN_ID),
                new KnowledgeRetrievePlugin.Request(query, maxResults),
                KnowledgeRetrievePlugin.Result.class
        );
        if (result == null) {
            return KnowledgeReferenceRenderer.NO_KNOWLEDGE;
        }
        // 说明：有命中时返回带溯源标注的参考数据；无命中时只回状态码，便于调用方区分“无内容”与“无权限/未配置”。
        return KnowledgeReferenceRenderer.hasKnowledge(result)
                ? KnowledgeReferenceRenderer.render(result)
                : result.status();
    }

    private AgentExecutionContext toolContext(String tenantId, String pluginId) {
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("工具调用必须显式携带租户标识: " + pluginId);
        }
        return new AgentExecutionContext(UUID.randomUUID().toString(), UUID.randomUUID().toString(), tenantId, null, null);
    }

    private AgentRunControl toolControl(String pluginId) {
        return new AgentRunControl(new AgentRunPolicy(
                Set.of(pluginId), 1, AgentCallerType.SYSTEM, 3, 3, 10_000L));
    }
}
