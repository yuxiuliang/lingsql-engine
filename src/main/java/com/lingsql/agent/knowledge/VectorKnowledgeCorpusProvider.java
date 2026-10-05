package com.lingsql.agent.knowledge;

import com.lingsql.agent.plugin.KnowledgeRetrievePlugin;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 向量语义检索提供者：把知识文档切块向量化后写入 Elasticsearch，由 ES HNSW 索引承担近似最近邻。
 *
 * <p>性能取舍：ES 8.x 的 {@code knn} 查询在亿级向量下 P99 可控，且水平扩容不需要应用层改造；
 * 相比在 MySQL 里做应用层余弦相似度扫描（数据量上来必然全表扫 + 线性算距离），
 * 这是「数据大了不卡」唯一不需要重写的方案。</p>
 *
 * <p>租户隔离：每次检索强制注入 {@code tenant_id} 与 {@code status} 过滤表达式，
 * 过滤在 ES 侧完成，不依赖应用层事后剔除。</p>
 *
 * @author 余炘洋
 * @date 2026-10-05
 */
@Slf4j
@Service
@ConditionalOnBean(VectorStore.class)
@RequiredArgsConstructor
public class VectorKnowledgeCorpusProvider implements KnowledgeRetrievePlugin.KnowledgeCorpusProvider {

    private static final int MAX_TOP_K = 20;

    private final VectorStore vectorStore;

    @Override
    public KnowledgeRetrievePlugin.Result retrieve(String tenantId, String query, int maxResults) {
        if (tenantId == null || tenantId.isBlank()) {
            return new KnowledgeRetrievePlugin.Result(false, "TENANT_REQUIRED", List.of());
        }
        int topK = Math.min(Math.max(maxResults, 1), MAX_TOP_K);
        FilterExpressionBuilder builder = new FilterExpressionBuilder();
        Filter.Expression filter = builder.and(
                        builder.eq("tenant_id", tenantId),
                        builder.eq("status", "PUBLISHED"))
                .build();
        SearchRequest request = SearchRequest.builder()
                .query(query)
                .topK(topK)
                .filterExpression(filter)
                .build();
        List<Document> hits = vectorStore.similaritySearch(request);
        List<String> snippets = hits.stream()
                .map(doc -> doc.getText())
                .limit(topK)
                .toList();
        return new KnowledgeRetrievePlugin.Result(!snippets.isEmpty(), snippets.isEmpty() ? "NO_HIT" : "OK", snippets);
    }
}