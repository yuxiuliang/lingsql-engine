package com.lingsql.agent.knowledge;

import com.lingsql.agent.plugin.KnowledgeRetrievePlugin;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据库版知识检索提供者：租户过滤 + 关键词召回，失败关闭时不返回任何内容。
 *
 * <p>召回策略：把问题切成确定性词项后逐词项查询（每词项都强制租户与发布状态过滤），
 * 合并去重并按命中词项数排序，再把结果裁到目标条数。命中数打分的目的是让
 * 「同时命中多个词项」的文档排在前面，避免单字巧合命中挤占上下文。</p>
 *
 * <p>装配顺序排在向量检索之后：只有未配置向量库时才作为兜底语料来源，
 * 避免同时装配两个实现时出现候选歧义。</p>
 *
 * @author 余炘洋
 * @date 2026-10-05
 */
@Service
@Order(100)
@RequiredArgsConstructor
public class DbKnowledgeCorpusProvider implements KnowledgeRetrievePlugin.KnowledgeCorpusProvider {

    private static final Comparator<KnowledgeDocument> BY_ID =
            Comparator.comparing(KnowledgeDocument::getId, Comparator.nullsLast(Comparator.naturalOrder()));

    private final KnowledgeDocumentRepository repository;

    @Override
    public KnowledgeRetrievePlugin.Result retrieve(String tenantId, String query, int maxResults) {
        if (tenantId == null || tenantId.isBlank() || query == null || query.isBlank()) {
            return new KnowledgeRetrievePlugin.Result(false, "TENANT_REQUIRED", List.of());
        }
        int limit = Math.max(0, maxResults);
        if (limit == 0) {
            return new KnowledgeRetrievePlugin.Result(false, "NO_HIT", List.of());
        }
        List<String> terms = KnowledgeQueryTerms.extract(query);
        if (terms.isEmpty()) {
            return new KnowledgeRetrievePlugin.Result(false, "NO_HIT", List.of());
        }
        Map<Long, KnowledgeDocument> matched = new LinkedHashMap<>();
        Map<Long, Integer> termHits = new HashMap<>();
        for (String term : terms) {
            List<KnowledgeDocument> documents = repository.search(tenantId, term);
            if (documents == null) {
                continue;
            }
            for (KnowledgeDocument document : documents) {
                if (document == null || document.getId() == null) {
                    continue;
                }
                matched.putIfAbsent(document.getId(), document);
                termHits.merge(document.getId(), 1, Integer::sum);
            }
        }
        List<KnowledgeRetrievePlugin.KnowledgeHit> hits = matched.values().stream()
                .sorted(Comparator
                        .comparingInt((KnowledgeDocument document) -> termHits.getOrDefault(document.getId(), 0))
                        .reversed()
                        .thenComparing(BY_ID))
                .limit(limit)
                .map(document -> KnowledgeRetrievePlugin.toHit(
                        String.valueOf(document.getId()),
                        document.getTitle(),
                        document.getVersion(),
                        KnowledgeRetrievePlugin.SOURCE_DB_CORPUS,
                        document.getContent()))
                .filter(hit -> hit.documentId() != null && !hit.snippet().isEmpty())
                .toList();
        return new KnowledgeRetrievePlugin.Result(
                !hits.isEmpty(),
                hits.isEmpty() ? "NO_HIT" : "OK",
                hits);
    }
}
