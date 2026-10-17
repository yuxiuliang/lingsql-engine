package com.lingsql.agent.tool;

import com.lingsql.agent.plugin.AgentPluginException;
import com.lingsql.agent.plugin.AgentPluginExecutor;
import com.lingsql.agent.plugin.AgentPluginRegistry;
import com.lingsql.agent.plugin.CalculateMetricPlugin;
import com.lingsql.agent.plugin.KnowledgeRetrievePlugin;
import com.lingsql.agent.service.AgentPluginGrantService;
import com.lingsql.agent.service.PluginInvocationOutboxService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * AgentToolProvider 单元测试：强制租户上下文并统一经过插件网关。
 *
 * @author 余炘洋
 * @date 2026-10-05
 */
class AgentToolProviderTest {

    private final AgentPluginGrantService grantService = mock(AgentPluginGrantService.class);

    private final AgentPluginExecutor pluginExecutor = new AgentPluginExecutor(
            new AgentPluginRegistry(List.of(
                    new CalculateMetricPlugin(),
                    new KnowledgeRetrievePlugin(Optional.empty()))),
            mock(PluginInvocationOutboxService.class),
            grantService);

    private final AgentToolProvider provider = new AgentToolProvider(pluginExecutor);

    @Test
    void calculateMetricRejectsMissingTenant() {
        assertThrows(IllegalArgumentException.class, () -> provider.calculateMetric(null, "1+1"));
        assertThrows(IllegalArgumentException.class, () -> provider.calculateMetric("", "1+1"));
    }

    @Test
    void calculateMetricReturnsValueWithTenant() {
        when(grantService.isGranted(anyString(), anyString())).thenReturn(true);

        assertEquals("3.0", provider.calculateMetric("tenant-a", "1+2"));
    }

    @Test
    void calculateMetricRejectsTenantWithoutGrant() {
        when(grantService.isGranted(anyString(), anyString())).thenReturn(false);

        assertThrows(AgentPluginException.class, () -> provider.calculateMetric("tenant-a", "1+2"));
    }

    @Test
    void knowledgeRetrieveFailsClosedWithoutCorpus() {
        when(grantService.isGranted(anyString(), anyString())).thenReturn(true);

        assertEquals("NO_CORPUS_CONFIGURED", provider.knowledgeRetrieve("tenant-a", "口径", 3));
    }

    @Test
    void knowledgeRetrieveRejectsMissingTenant() {
        assertThrows(IllegalArgumentException.class, () -> provider.knowledgeRetrieve(null, "口径", 3));
    }
}
