package com.lingsql.agent.knowledge;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 知识文档向量化入库服务：把已发布文档切块后写入向量库。
 *
 * <p>切块策略固定为按字符窗口重叠切分，保证同一段落在边界处不丢语义；
 * 每个分块强制携带 {@code tenant_id} 与 {@code status} 元数据，
 * 这样检索侧的租户过滤才能在向量库索引层生效。</p>
 *
 * <p>未装配向量库时本服务不会被创建，避免应用在没有 ES 时启动失败。</p>
 *
 * @author 余炘洋
 * @date 2026-10-05
 */
@Slf4j
@Service
@ConditionalOnBean(VectorStore.class)
@RequiredArgsConstructor
public class KnowledgeIndexingService {

    private static final int CHUNK_SIZE = 800;
    private static final int CHUNK_OVERLAP = 120;
    private static final int MAX_CHUNKS_PER_DOCUMENT = 200;

    private final VectorStore vectorStore;

    /**
     * 将已发布文档写入向量库。
     *
     * @param tenantId 租户标识
     * @param title    标题
     * @param content  正文
     * @param version  版本
     * @return 实际写入的分块数
     */
    public int indexPublished(String tenantId, String title, String content, String version) {
        if (tenantId == null || tenantId.isBlank() || content == null || content.isBlank()) {
            return 0;
        }
        List<Document> chunks = split(tenantId, title, content, version);
        if (chunks.isEmpty()) {
            return 0;
        }
        vectorStore.add(chunks);
        log.info("知识文档已向量化: tenantId={}, title={}, chunks={}", tenantId, title, chunks.size());
        return chunks.size();
    }

    /**
     * 切块并注入租户元数据。
     */
    List<Document> split(String tenantId, String title, String content, String version) {
        List<Document> chunks = new ArrayList<>();
        int start = 0;
        int length = content.length();
        while (start < length && chunks.size() < MAX_CHUNKS_PER_DOCUMENT) {
            int end = Math.min(start + CHUNK_SIZE, length);
            String text = content.substring(start, end);
            Map<String, Object> metadata = new java.util.LinkedHashMap<>();
            metadata.put("tenant_id", tenantId);
            metadata.put("status", "PUBLISHED");
            metadata.put("title", title == null ? "" : title);
            metadata.put("version", version == null ? "" : version);
            metadata.put("chunk_index", chunks.size());
            chunks.add(new Document(text, metadata));
            if (end == length) {
                break;
            }
            start = end - CHUNK_OVERLAP;
            if (start < 0) {
                start = 0;
            }
        }
        return chunks;
    }
}