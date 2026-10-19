package com.lingsql.agent.plugin;

import com.lingsql.agent.knowledge.DbKnowledgeCorpusProvider;
import com.lingsql.agent.knowledge.KnowledgeDocument;
import com.lingsql.agent.knowledge.KnowledgeDocumentRepository;
import com.lingsql.agent.orchestration.AgentCallerType;
import com.lingsql.agent.orchestration.AgentExecutionContext;
import com.lingsql.agent.orchestration.AgentRunControl;
import com.lingsql.agent.orchestration.AgentRunPolicy;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 受控知识检索插件单元测试：语料提供者装配顺序、溯源强制与失败关闭。
 *
 * @author 余炘洋
 * @date 2026-10-19
 */
class KnowledgeRetrievePluginTest {

    private AgentRunControl control() {
        return new AgentRunControl(new AgentRunPolicy(
                Set.of(KnowledgeRetrievePlugin.PLUGIN_ID), 5,
                AgentCallerType.SYSTEM, 10, 3, 60_000L));
    }

    private AgentExecutionContext context() {
        return new AgentExecutionContext("exec-1", "req-1", "tenant-1", 1L, "conv-1");
    }

    @Test
    void shouldRejectBlankQuery() {
        KnowledgeRetrievePlugin plugin = new KnowledgeRetrievePlugin(List.of());

        AgentPluginException exception = assertThrows(AgentPluginException.class,
                () -> plugin.execute(context(), control(), new KnowledgeRetrievePlugin.Request("  ", 5)));

        assertEquals(AgentPluginFailureReason.CONTRACT_VIOLATION, exception.getReason());
    }

    @Test
    void shouldRejectMissingTenant() {
        KnowledgeRetrievePlugin plugin = new KnowledgeRetrievePlugin(List.of());

        AgentPluginException exception = assertThrows(AgentPluginException.class,
                () -> plugin.execute(
                        new AgentExecutionContext("exec-1", "req-1", null, 1L, "conv-1"),
                        control(),
                        new KnowledgeRetrievePlugin.Request("口径", 5)));

        assertEquals(AgentPluginFailureReason.CONTRACT_VIOLATION, exception.getReason());
    }

    @Test
    void shouldReportNoCorpusWhenNoProviderConfigured() {
        KnowledgeRetrievePlugin plugin = new KnowledgeRetrievePlugin(List.of());

        KnowledgeRetrievePlugin.Result result = plugin.execute(
                context(), control(), new KnowledgeRetrievePlugin.Request("口径", 5));

        assertFalse(result.success());
        assertEquals("NO_CORPUS_CONFIGURED", result.status());
        assertTrue(result.hits().isEmpty());
    }

    @Test
    void shouldUseFirstConfiguredProviderAndCapResults() {
        KnowledgeRetrievePlugin.KnowledgeCorpusProvider first = (tenantId, query, maxResults) ->
                new KnowledgeRetrievePlugin.Result(true, "OK", java.util.stream.IntStream.range(0, 8)
                        .mapToObj(i -> KnowledgeRetrievePlugin.toHit(
                                "doc-" + i, "口径" + i, "1.0", "DB_CORPUS", "内容" + i))
                        .limit(maxResults)
                        .toList());
        KnowledgeRetrievePlugin.KnowledgeCorpusProvider second = (tenantId, query, maxResults) ->
                new KnowledgeRetrievePlugin.Result(true, "OK", List.of());
        KnowledgeRetrievePlugin plugin = new KnowledgeRetrievePlugin(List.of(first, second));

        KnowledgeRetrievePlugin.Result result = plugin.execute(
                context(), control(), new KnowledgeRetrievePlugin.Request("口径", 3));

        assertTrue(result.success());
        assertEquals(3, result.hits().size());
        assertEquals("doc-0", result.hits().get(0).documentId());
    }

    @Test
    void shouldDropHitsWithoutTraceableSource() {
        KnowledgeRetrievePlugin.KnowledgeCorpusProvider provider = (tenantId, query, maxResults) ->
                new KnowledgeRetrievePlugin.Result(true, "OK", List.of(
                        new KnowledgeRetrievePlugin.KnowledgeHit(null, "标题", "1.0", "DB_CORPUS", "无文档标识"),
                        new KnowledgeRetrievePlugin.KnowledgeHit("doc-2", "标题", "1.0", "DB_CORPUS", "  "),
                        new KnowledgeRetrievePlugin.KnowledgeHit("doc-3", "", "1.0", "DB_CORPUS", "无标题"),
                        new KnowledgeRetrievePlugin.KnowledgeHit("doc-4", "标题", "1.0", "", "无来源"),
                        new KnowledgeRetrievePlugin.KnowledgeHit("doc-5", "标题", "1.0", "DB_CORPUS", "保留")));
        KnowledgeRetrievePlugin plugin = new KnowledgeRetrievePlugin(List.of(provider));

        KnowledgeRetrievePlugin.Result result = plugin.execute(
                context(), control(), new KnowledgeRetrievePlugin.Request("口径", 5));

        assertEquals(1, result.hits().size());
        assertEquals("doc-5", result.hits().get(0).documentId());
    }

    @Test
    void shouldFailClosedWhenProviderReturnsNull() {
        KnowledgeRetrievePlugin plugin = new KnowledgeRetrievePlugin(
                List.of((tenantId, query, maxResults) -> null));

        KnowledgeRetrievePlugin.Result result = plugin.execute(
                context(), control(), new KnowledgeRetrievePlugin.Request("口径", 5));

        assertFalse(result.success());
        assertEquals("NO_HIT", result.status());
    }

    @Test
    void shouldNormalizeMaxResultsWithinBounds() {
        List<Integer> captured = new java.util.ArrayList<>();
        KnowledgeRetrievePlugin.KnowledgeCorpusProvider provider = (tenantId, query, maxResults) -> {
            captured.add(maxResults);
            return new KnowledgeRetrievePlugin.Result(false, "NO_HIT", List.of());
        };
        KnowledgeRetrievePlugin plugin = new KnowledgeRetrievePlugin(List.of(provider));

        plugin.execute(context(), control(), new KnowledgeRetrievePlugin.Request("口径", 9999));
        plugin.execute(context(), control(), new KnowledgeRetrievePlugin.Request("口径", 0));
        plugin.execute(context(), control(), new KnowledgeRetrievePlugin.Request("口径", 3));

        assertEquals(List.of(20, 1, 3), captured);
    }

    @Test
    void shouldTruncateSnippetAndNormalizeFields() {
        String longContent = "知".repeat(KnowledgeRetrievePlugin.MAX_SNIPPET_LENGTH + 50);

        KnowledgeRetrievePlugin.KnowledgeHit hit = KnowledgeRetrievePlugin.toHit(
                " doc-9 ", " 标题 ", " 1.0 ", " DB_CORPUS ", longContent);

        assertEquals("doc-9", hit.documentId());
        assertEquals("标题", hit.title());
        assertEquals("1.0", hit.version());
        assertEquals("DB_CORPUS", hit.source());
        assertEquals(KnowledgeRetrievePlugin.MAX_SNIPPET_LENGTH + 1, hit.snippet().length());
        assertTrue(hit.snippet().endsWith("…"));
    }

    @Test
    void databaseProviderShouldOrderDeterministicallyAndCarryProvenance() {
        KnowledgeDocumentRepository repository = mock(KnowledgeDocumentRepository.class);
        KnowledgeDocument later = new KnowledgeDocument();
        later.setId(9L);
        later.setTitle("口径B");
        later.setContent("内容B");
        later.setVersion("2.0");
        KnowledgeDocument earlier = new KnowledgeDocument();
        earlier.setId(2L);
        earlier.setTitle("口径A");
        earlier.setContent("内容A");
        earlier.setVersion("1.0");
        when(repository.search(anyString(), anyString())).thenReturn(List.of(later, earlier));
        KnowledgeRetrievePlugin plugin = new KnowledgeRetrievePlugin(
                List.of(new DbKnowledgeCorpusProvider(repository)));

        KnowledgeRetrievePlugin.Result result = plugin.execute(
                context(), control(), new KnowledgeRetrievePlugin.Request("口径", 5));

        assertEquals(2, result.hits().size());
        assertEquals("2", result.hits().get(0).documentId());
        assertEquals("9", result.hits().get(1).documentId());
        assertEquals("DB_CORPUS", result.hits().get(0).source());
    }

    @Test
    void databaseProviderShouldRejectMissingTenant() {
        KnowledgeDocumentRepository repository = mock(KnowledgeDocumentRepository.class);
        KnowledgeRetrievePlugin plugin = new KnowledgeRetrievePlugin(
                List.of(new DbKnowledgeCorpusProvider(repository)));

        // 说明：租户缺失必须 fail-closed，且在进入语料提供者前就被拒绝，不访问数据库。
        AgentPluginException exception = assertThrows(AgentPluginException.class,
                () -> plugin.execute(
                        new AgentExecutionContext("exec-1", "req-1", " ", 1L, "conv-1"),
                        control(),
                        new KnowledgeRetrievePlugin.Request("口径", 5)));
        assertEquals(AgentPluginFailureReason.CONTRACT_VIOLATION, exception.getReason());
        verify(repository, never()).search(anyString(), anyString());
    }
}
