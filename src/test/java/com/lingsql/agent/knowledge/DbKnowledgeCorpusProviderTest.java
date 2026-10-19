package com.lingsql.agent.knowledge;

import com.lingsql.agent.plugin.KnowledgeRetrievePlugin;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 数据库版知识检索提供者单元测试：多词项召回、命中数排序、条数裁剪与租户失败关闭。
 *
 * @author 余炘洋
 * @date 2026-10-19
 */
class DbKnowledgeCorpusProviderTest {

    private final KnowledgeDocumentRepository repository = mock(KnowledgeDocumentRepository.class);

    private final DbKnowledgeCorpusProvider provider = new DbKnowledgeCorpusProvider(repository);

    private KnowledgeDocument document(Long id, String title, String content, String version) {
        KnowledgeDocument document = new KnowledgeDocument();
        document.setId(id);
        document.setTitle(title);
        document.setContent(content);
        document.setVersion(version);
        return document;
    }

    @Test
    void shouldRejectMissingTenantWithoutQueryingDatabase() {
        KnowledgeRetrievePlugin.Result result = provider.retrieve(" ", "口径", 5);

        assertFalse(result.success());
        assertEquals("TENANT_REQUIRED", result.status());
        verify(repository, never()).search(anyString(), anyString());
    }

    @Test
    void shouldMergeTermsAndRankByHitCount() {
        // 说明：命中多个词项的文档必须排在只命中单个词项的文档之前。
        KnowledgeDocument multiHit = document(20L, "退款口径", "订单退款金额按原支付渠道退回", "2.0");
        KnowledgeDocument singleHit = document(3L, "订单口径", "订单按下单时间统计", "1.0");
        when(repository.search("t1", "订单")).thenReturn(List.of(multiHit, singleHit));
        when(repository.search("t1", "退款")).thenReturn(List.of(multiHit));

        KnowledgeRetrievePlugin.Result result = provider.retrieve("t1", "订单退款金额怎么统计", 5);

        assertTrue(result.success());
        assertEquals("OK", result.status());
        assertEquals(2, result.hits().size());
        assertEquals("20", result.hits().get(0).documentId());
        assertEquals("3", result.hits().get(1).documentId());
        assertEquals("DB_CORPUS", result.hits().get(0).source());
        assertEquals("2.0", result.hits().get(0).version());
    }

    @Test
    void shouldTrimHitsToRequestedLimit() {
        KnowledgeDocument first = document(1L, "口径A", "订单口径A", "1.0");
        KnowledgeDocument second = document(2L, "口径B", "订单口径B", "1.0");
        when(repository.search("t1", "订单")).thenReturn(List.of(first, second));

        KnowledgeRetrievePlugin.Result result = provider.retrieve("t1", "订单", 1);

        assertEquals(1, result.hits().size());
        assertEquals("1", result.hits().get(0).documentId());
    }

    @Test
    void shouldIgnoreDocumentWithoutId() {
        KnowledgeDocument dangling = document(null, "无主键文档", "订单口径", "1.0");
        when(repository.search("t1", "订单")).thenReturn(List.of(dangling));

        KnowledgeRetrievePlugin.Result result = provider.retrieve("t1", "订单", 5);

        assertFalse(result.success());
        assertEquals("NO_HIT", result.status());
    }

    @Test
    void shouldReturnNoHitWhenQuestionHasNoUsableTerm() {
        KnowledgeRetrievePlugin.Result result = provider.retrieve("t1", "？？？", 5);

        assertFalse(result.success());
        assertEquals("NO_HIT", result.status());
        verify(repository, never()).search(anyString(), anyString());
    }

    @Test
    void shouldReturnNoHitWhenLimitIsZero() {
        KnowledgeRetrievePlugin.Result result = provider.retrieve("t1", "订单", 0);

        assertFalse(result.success());
        assertEquals("NO_HIT", result.status());
        verify(repository, never()).search(anyString(), anyString());
    }
}
