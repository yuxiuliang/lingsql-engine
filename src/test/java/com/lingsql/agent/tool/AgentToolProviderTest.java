package com.lingsql.agent.tool;

import com.lingsql.agent.plugin.CalculateMetricPlugin;
import com.lingsql.agent.plugin.KnowledgeRetrievePlugin;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AgentToolProvider 单元测试：强制租户上下文。
 *
 * @author 余炘洋
 * @date 2026-10-05
 */
class AgentToolProviderTest {

    private final AgentToolProvider provider = new AgentToolProvider(
            new CalculateMetricPlugin(),
            new KnowledgeRetrievePlugin(Optional.empty()));

    @Test
    void calculateMetricRejectsMissingTenant() {
        assertThrows(IllegalArgumentException.class, () -> provider.calculateMetric(null, "1+1"));
        assertThrows(IllegalArgumentException.class, () -> provider.calculateMetric("", "1+1"));
    }

    @Test
    void calculateMetricReturnsValueWithTenant() {
        assertEquals("3.0", provider.calculateMetric("tenant-a", "1+2"));
    }

    @Test
    void knowledgeRetrieveFailsClosedWithoutCorpus() {
        assertEquals("NO_CORPUS_CONFIGURED", provider.knowledgeRetrieve("tenant-a", "口径", 3));
    }

    @Test
    void knowledgeRetrieveRejectsMissingTenant() {
        assertThrows(IllegalArgumentException.class, () -> provider.knowledgeRetrieve(null, "口径", 3));
    }
}