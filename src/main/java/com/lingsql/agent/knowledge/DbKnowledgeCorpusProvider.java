package com.lingsql.agent.knowledge;

import com.lingsql.agent.plugin.KnowledgeRetrievePlugin;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 数据库版知识检索提供者：租户过滤 + 简单关键词召回，失败关闭时不返回任何内容。
 *
 * @author 余炘洋
 * @date 2026-10-05
 */
@Service
@RequiredArgsConstructor
public class DbKnowledgeCorpusProvider implements KnowledgeRetrievePlugin.KnowledgeCorpusProvider {

    private final KnowledgeDocumentRepository repository;

    @Override
    public KnowledgeRetrievePlugin.Result retrieve(String tenantId, String query, int maxResults) {
        List<KnowledgeDocument> hits = repository.search(tenantId, query);
        List<String> snippets = hits.stream()
                .limit(Math.max(0, maxResults))
                .map(d -> d.getTitle() + (d.getVersion() == null ? "" : " (v" + d.getVersion() + ")") + ": " + (d.getContent() == null ? "" : d.getContent()))
                .toList();
        return new KnowledgeRetrievePlugin.Result(!snippets.isEmpty(), snippets.isEmpty() ? "NO_HIT" : "OK", snippets);
    }
}
