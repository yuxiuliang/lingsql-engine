package com.lingsql.agent.knowledge;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 知识检索关键词抽取单元测试：中文滑窗、英文分词、去重、上限与排序确定性。
 *
 * @author 余炘洋
 * @date 2026-10-19
 */
class KnowledgeQueryTermsTest {

    @Test
    void shouldSplitChineseIntoBigrams() {
        List<String> terms = KnowledgeQueryTerms.extract("销售额口径");

        assertEquals(List.of("销售", "售额", "额口", "口径"), terms);
    }

    @Test
    void shouldCapTermsAtDefaultLimit() {
        List<String> terms = KnowledgeQueryTerms.extract("上个月已支付订单的退款金额怎么统计");

        assertEquals(KnowledgeQueryTerms.DEFAULT_MAX_TERMS, terms.size());
        assertTrue(terms.stream().allMatch(term -> !term.isBlank()));
    }

    @Test
    void shouldKeepShortChineseRunAsWholeTermToo() {
        List<String> terms = KnowledgeQueryTerms.extract("口径");

        assertEquals(List.of("口径"), terms);
    }

    @Test
    void shouldSplitOnPunctuationAndKeepWords() {
        List<String> terms = KnowledgeQueryTerms.extract("order_amount 是什么？ refund 呢");

        assertTrue(terms.contains("order_amount"));
        assertTrue(terms.contains("refund"));
        assertTrue(terms.contains("什么"));
    }

    @Test
    void shouldDropSingleCharacterTerms() {
        List<String> terms = KnowledgeQueryTerms.extract("a 的 b 口径");

        assertTrue(terms.contains("口径"));
        assertTrue(terms.stream().noneMatch(term -> term.length() == 1));
    }

    @Test
    void shouldTruncateOverLongAsciiToken() {
        String longToken = "x".repeat(KnowledgeQueryTerms.MAX_TERM_LENGTH + 5);

        List<String> terms = KnowledgeQueryTerms.extract(longToken);

        assertEquals(List.of("x".repeat(KnowledgeQueryTerms.MAX_TERM_LENGTH)), terms);
    }

    @Test
    void shouldReturnEmptyForBlankQuestion() {
        assertEquals(List.of(), KnowledgeQueryTerms.extract(null));
        assertEquals(List.of(), KnowledgeQueryTerms.extract("   "));
        assertEquals(List.of(), KnowledgeQueryTerms.extract("？？？"));
        assertEquals(List.of(), KnowledgeQueryTerms.extract("口径", 0));
    }

    @Test
    void shouldBeDeterministicForSameQuestion() {
        String question = "上个月已支付订单的退款金额怎么统计";

        assertEquals(KnowledgeQueryTerms.extract(question), KnowledgeQueryTerms.extract(question));
    }

    @Test
    void shouldDropDigitOnlyBigramsAndKeepTopicTerms() {
        // 说明：日期型前缀（2026/8）不参与检索，主题词必须在采样后保留，否则兜底召回形同虚设。
        List<String> terms = KnowledgeQueryTerms.extract("2026年8月已完成订单的销售额是多少");

        assertTrue(terms.contains("销售"), "主题词应保留: " + terms);
        assertTrue(terms.stream().noneMatch(term -> term.matches("\\d+")), "不应包含纯数字词项: " + terms);
        assertTrue(terms.size() <= KnowledgeQueryTerms.DEFAULT_MAX_TERMS);
    }
}
