package com.lingsql.agent.plugin;

import com.lingsql.agent.orchestration.AgentExecutionContext;
import com.lingsql.agent.orchestration.AgentCallerType;
import com.lingsql.agent.orchestration.AgentRunControl;
import com.lingsql.agent.orchestration.AgentRunPolicy;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 新插件与预算控制的单元测试。
 *
 * @author 余炘洋
 * @date 2026-10-05
 */
class PluginGatewayFeatureTest {

    private final CalculateMetricPlugin metricPlugin = new CalculateMetricPlugin();
    private final ChartMapperPlugin chartMapperPlugin = new ChartMapperPlugin();
    private final KnowledgeRetrievePlugin knowledgePlugin = new KnowledgeRetrievePlugin(java.util.List.of());

    private AgentExecutionContext context() {
        return new AgentExecutionContext("exec-1", "req-1", "tenant-1", 1L, "conv-1");
    }

    private AgentRunControl control() {
        return new AgentRunControl(new AgentRunPolicy(
                Set.of(CalculateMetricPlugin.PLUGIN_ID, ChartMapperPlugin.PLUGIN_ID, KnowledgeRetrievePlugin.PLUGIN_ID),
                10, AgentCallerType.SYSTEM, 10, 3, 60_000L, 10_000L, 1_000L));
    }

    @Test
    void calculateMetricEvaluatesAviatorExpression() {
        var result = metricPlugin.execute(context(), control(),
                new CalculateMetricPlugin.Request("a + b * 2", Map.of("a", 1, "b", 3)));
        assertTrue(result.success());
        assertEquals(7, ((Number) result.value()).intValue());
    }

    @Test
    void chartMapperProducesDeterministicOption() {
        var result = chartMapperPlugin.execute(context(), control(),
                new ChartMapperPlugin.Request("销售额", "bar", List.of(
                        Map.of("month", "一月", "amount", 100),
                        Map.of("month", "二月", "amount", 200)
                )));
        assertTrue(result.success());
        Map<?, ?> xAxis = (Map<?, ?>) result.option().get("xAxis");
        assertEquals(List.of("一月", "二月"), xAxis.get("data"));
    }

    @Test
    void knowledgeRetrieveFailsClosedWithoutCorpus() {
        var result = knowledgePlugin.execute(context(), control(),
                new KnowledgeRetrievePlugin.Request("查询", 5));
        assertFalse(result.success());
        assertEquals("NO_CORPUS_CONFIGURED", result.status());
    }

    @Test
    void agentRunControlEnforcesTokenBudget() {
        AgentRunControl control = new AgentRunControl(new AgentRunPolicy(
                Set.of(), 10, AgentCallerType.SYSTEM, 10, 3, 60_000L, 5L, 100L));
        control.recordUsage(4L, 0L);
        assertThrows(AgentPluginException.class, () -> control.recordUsage(2L, 0L));
    }
}
