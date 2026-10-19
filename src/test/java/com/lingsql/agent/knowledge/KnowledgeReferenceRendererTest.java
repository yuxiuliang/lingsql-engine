package com.lingsql.agent.knowledge;

import com.lingsql.agent.plugin.KnowledgeRetrievePlugin;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 知识参考数据渲染器单元测试：溯源标注、条数与长度截断、空结果占位。
 *
 * @author 余炘洋
 * @date 2026-10-19
 */
class KnowledgeReferenceRendererTest {

    @Test
    void shouldRenderTitleVersionSourceAndDocumentId() {
        KnowledgeRetrievePlugin.Result result = new KnowledgeRetrievePlugin.Result(true, "OK", List.of(
                new KnowledgeRetrievePlugin.KnowledgeHit("7", "销售额口径", "1.0", "DB_CORPUS", "按自然月统计")));

        assertEquals("[1] 销售额口径（版本 1.0）（来源 DB_CORPUS，文档 7）：按自然月统计",
                KnowledgeReferenceRenderer.render(result));
        assertTrue(KnowledgeReferenceRenderer.hasKnowledge(result));
    }

    @Test
    void shouldOmitVersionWhenAbsent() {
        KnowledgeRetrievePlugin.Result result = new KnowledgeRetrievePlugin.Result(true, "OK", List.of(
                new KnowledgeRetrievePlugin.KnowledgeHit("7", "销售口径", "", "DB_CORPUS", "按自然月统计")));

        String rendered = KnowledgeReferenceRenderer.render(result);

        assertTrue(rendered.contains("[1] 销售口径（来源 DB_CORPUS，文档 7）"));
        assertFalse(rendered.contains("版本"));
    }

    @Test
    void shouldRenderMultipleHitsWithStableIndexes() {
        KnowledgeRetrievePlugin.Result result = new KnowledgeRetrievePlugin.Result(true, "OK", List.of(
                new KnowledgeRetrievePlugin.KnowledgeHit("1", "口径A", "1.0", "DB_CORPUS", "内容A"),
                new KnowledgeRetrievePlugin.KnowledgeHit("2", "口径B", "2.0", "VECTOR_STORE", "内容B")));

        String rendered = KnowledgeReferenceRenderer.render(result);

        assertEquals(2, rendered.lines().count());
        assertTrue(rendered.startsWith("[1] 口径A"));
        assertTrue(rendered.endsWith("[2] 口径B（版本 2.0）（来源 VECTOR_STORE，文档 2）：内容B"));
    }

    @Test
    void shouldCapHitsAtMaxHits() {
        KnowledgeRetrievePlugin.Result result = new KnowledgeRetrievePlugin.Result(true, "OK",
                IntStream.range(0, 12)
                        .mapToObj(i -> new KnowledgeRetrievePlugin.KnowledgeHit(
                                String.valueOf(i), "口径" + i, "1.0", "DB_CORPUS", "内容" + i))
                        .toList());

        String rendered = KnowledgeReferenceRenderer.render(result);

        assertEquals(KnowledgeReferenceRenderer.MAX_HITS, rendered.lines().count());
        assertTrue(rendered.contains("[5] 口径4"));
        assertFalse(rendered.contains("[6]"));
    }

    @Test
    void shouldCapTotalLength() {
        String longSnippet = "知".repeat(KnowledgeRetrievePlugin.MAX_SNIPPET_LENGTH);
        KnowledgeRetrievePlugin.Result result = new KnowledgeRetrievePlugin.Result(true, "OK",
                IntStream.range(0, 10)
                        .mapToObj(i -> new KnowledgeRetrievePlugin.KnowledgeHit(
                                String.valueOf(i), "口径" + i, "1.0", "DB_CORPUS", longSnippet))
                        .toList());

        String rendered = KnowledgeReferenceRenderer.render(result);

        assertTrue(rendered.length() <= KnowledgeReferenceRenderer.MAX_LENGTH);
        assertTrue(rendered.lines().count() < KnowledgeReferenceRenderer.MAX_HITS);
    }

    @Test
    void shouldReturnPlaceholderForEmptyOrUnsuccessfulResult() {
        assertEquals(KnowledgeReferenceRenderer.NO_KNOWLEDGE,
                KnowledgeReferenceRenderer.render(null));
        assertEquals(KnowledgeReferenceRenderer.NO_KNOWLEDGE,
                KnowledgeReferenceRenderer.render(new KnowledgeRetrievePlugin.Result(false, "NO_HIT", List.of())));
        assertEquals(KnowledgeReferenceRenderer.NO_KNOWLEDGE,
                KnowledgeReferenceRenderer.render(new KnowledgeRetrievePlugin.Result(false, "NO_HIT", null)));
        assertFalse(KnowledgeReferenceRenderer.hasKnowledge((KnowledgeRetrievePlugin.Result) null));
        assertFalse(KnowledgeReferenceRenderer.hasKnowledge(KnowledgeReferenceRenderer.NO_KNOWLEDGE));
    }
}
