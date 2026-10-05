package com.lingsql.agent.knowledge;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

/**
 * 知识文档入库单元测试：切块重叠、租户元数据注入与边界拒绝。
 *
 * @author 余炘洋
 * @date 2026-10-05
 */
class KnowledgeIndexingServiceTest {

    private final List<Document> written = new ArrayList<>();
    private final VectorStore vectorStore = mock(VectorStore.class);

    private KnowledgeIndexingService service() {
        doAnswer(invocation -> {
            written.addAll(invocation.getArgument(0));
            return null;
        }).when(vectorStore).add(anyList());
        return new KnowledgeIndexingService(vectorStore);
    }

    @Test
    void shortDocumentProducesSingleChunk() {
        assertEquals(1, service().indexPublished("t1", "标题", "短内容", "1.0"));
        assertEquals(1, written.size());
        assertEquals("短内容", written.get(0).getText());
    }

    @Test
    void longDocumentIsSplitWithOverlap() {
        String content = "a".repeat(2000);
        int chunks = service().indexPublished("t1", "标题", content, "1.0");
        assertTrue(chunks > 1, "长文档应被切分为多个分块");
        assertEquals(800, written.get(0).getText().length());
        assertEquals(chunks, written.size());
    }

    @Test
    void everyChunkCarriesTenantAndStatusMetadata() {
        String content = "b".repeat(1500);
        service().indexPublished("tenant-x", "口径", content, "2.0");
        assertFalse(written.isEmpty());
        for (Document doc : written) {
            assertEquals("tenant-x", doc.getMetadata().get("tenant_id"));
            assertEquals("PUBLISHED", doc.getMetadata().get("status"));
            assertEquals("口径", doc.getMetadata().get("title"));
            assertEquals("2.0", doc.getMetadata().get("version"));
            assertNotNull(doc.getMetadata().get("chunk_index"));
        }
    }

    @Test
    void chunkIndexesAreSequential() {
        service().indexPublished("t1", "t", "c".repeat(2000), "1.0");
        for (int i = 0; i < written.size(); i++) {
            assertEquals(i, written.get(i).getMetadata().get("chunk_index"));
        }
    }

    @Test
    void rejectsMissingTenantOrContent() {
        KnowledgeIndexingService service = service();
        assertEquals(0, service.indexPublished(null, "标题", "内容", "1.0"));
        assertEquals(0, service.indexPublished("  ", "标题", "内容", "1.0"));
        assertEquals(0, service.indexPublished("t1", "标题", "   ", "1.0"));
        assertEquals(0, service.indexPublished("t1", "标题", null, "1.0"));
        verify(vectorStore, never()).add(anyList());
    }

    @Test
    void chunkCountIsCapped() {
        String content = "x".repeat(800 * 400);
        int chunks = service().indexPublished("t1", "超大", content, "1.0");
        assertTrue(chunks <= 200, "分块数必须有上限，避免单文档打爆向量库");
    }
}