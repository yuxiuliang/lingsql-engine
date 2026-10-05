package com.lingsql.agent.knowledge;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 向量知识检索提供者单元测试：验证租户过滤注入与 topK 上限。
 *
 * @author 余炘洋
 * @date 2026-10-05
 */
class VectorKnowledgeCorpusProviderTest {

    private final VectorStore vectorStore = mock(VectorStore.class);

    @Test
    void retrieveRequiresTenant() {
        VectorKnowledgeCorpusProvider provider = new VectorKnowledgeCorpusProvider(vectorStore);
        var result = provider.retrieve(null, "口径", 5);
        assertFalse(result.success());
        assertEquals("TENANT_REQUIRED", result.status());
        verify(vectorStore, never()).similaritySearch(any(SearchRequest.class));
    }

    @Test
    void retrieveReturnsNoHitWhenEmpty() {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());
        VectorKnowledgeCorpusProvider provider = new VectorKnowledgeCorpusProvider(vectorStore);
        var result = provider.retrieve("t1", "口径", 5);
        assertFalse(result.success());
        assertEquals("NO_HIT", result.status());
    }

    @Test
    void retrieveReturnsHits() {
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(new Document("按自然月统计销售额")));
        VectorKnowledgeCorpusProvider provider = new VectorKnowledgeCorpusProvider(vectorStore);
        var result = provider.retrieve("t1", "口径", 5);
        assertTrue(result.success());
        assertEquals("OK", result.status());
        assertEquals(1, result.hits().size());
    }

    @Test
    void retrieveCapsTopKAtTwenty() {
        List<SearchRequest> captured = new java.util.ArrayList<>();
        VectorStore capturing = new VectorStore() {
            @Override public void add(List<Document> documents) { }
            @Override public void delete(List<String> idList) { }
            @Override public void delete(org.springframework.ai.vectorstore.filter.Filter.Expression filterExpression) { }
            @Override public List<Document> similaritySearch(SearchRequest request) {
                captured.add(request);
                return List.of();
            }
        };
        VectorKnowledgeCorpusProvider provider = new VectorKnowledgeCorpusProvider(capturing);
        provider.retrieve("t1", "口径", 9999);
        assertEquals(1, captured.size());
        assertEquals(20, captured.get(0).getTopK());
        assertTrue(captured.get(0).hasFilterExpression());
    }
}