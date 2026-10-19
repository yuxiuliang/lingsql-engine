package com.lingsql.agent.knowledge;

import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 知识检索关键词抽取：把自然语言问题切成可用于数据库关键词检索的确定性词项。
 *
 * <p>为什么需要：数据库兜底语料走的是 {@code LIKE '%词项%'} 匹配，直接用整句问题做模式
 * 几乎不可能命中任何文档；而中文没有空格分词，因此这里对连续的中文串取滑窗二字词，
 * 英文与数字按分隔符切词，再按词长优先截断，保证「命中更精确的词优先参与检索」。</p>
 *
 * <p>取舍：不引入分词组件或词典，只做确定性切分，代价是召回偏保守（宁可少召回，
 * 也不把不相关内容注入提示词）；词项数量与长度都有上限，避免放大数据库查询次数。</p>
 *
 * @author 余炘洋
 * @date 2026-10-19
 */
public final class KnowledgeQueryTerms {

    /**
     * 单次检索使用的最大词项数量。
     */
    public static final int DEFAULT_MAX_TERMS = 8;

    /**
     * 词项最小长度：单字符词噪声过大，直接丢弃。
     */
    public static final int MIN_TERM_LENGTH = 2;

    /**
     * 词项最大长度：超长词项既不利于匹配，也会放大索引扫描。
     */
    public static final int MAX_TERM_LENGTH = 32;

    private KnowledgeQueryTerms() {
    }

    /**
     * 按默认上限抽取词项。
     *
     * @param question 用户问题
     * @return 去重后的词项，按词长优先排序
     * @author 余炘洋
     * @date 2026-10-19
     */
    public static List<String> extract(String question) {
        return extract(question, DEFAULT_MAX_TERMS);
    }

    /**
     * 抽取词项。
     *
     * @param question 用户问题
     * @param maxTerms 词项数量上限
     * @return 去重后的词项，按词长优先排序
     * @author 余炘洋
     * @date 2026-10-19
     */
    public static List<String> extract(String question, int maxTerms) {
        if (!StringUtils.hasText(question) || maxTerms <= 0) {
            return List.of();
        }
        String normalized = question.trim().toLowerCase(Locale.ROOT);
        Set<String> terms = new LinkedHashSet<>();
        StringBuilder run = new StringBuilder();
        for (int index = 0; index < normalized.length(); index++) {
            char current = normalized.charAt(index);
            if (isSearchable(current)) {
                run.append(current);
            } else {
                collect(terms, run);
            }
        }
        collect(terms, run);
        if (terms.isEmpty()) {
            return List.of();
        }
        List<String> sorted = select(terms, maxTerms);
        return List.copyOf(sorted);
    }

    /**
     * 采样并排序词项：候选超出上限时按跨步均匀取样，避免只保留句首词而丢掉主题词。
     *
     * @param terms    候选词项（保持出现顺序）
     * @param maxTerms 上限
     * @return 词项列表
     * @author 余炘洋
     * @date 2026-10-19
     */
    private static List<String> select(Set<String> terms, int maxTerms) {
        List<String> candidates = new ArrayList<>(terms);
        if (candidates.size() > maxTerms) {
            int stride = (candidates.size() + maxTerms - 1) / maxTerms;
            List<String> sampled = new ArrayList<>();
            for (int index = 0; index < candidates.size() && sampled.size() < maxTerms; index += stride) {
                sampled.add(candidates.get(index));
            }
            candidates = sampled;
        }
        // 说明：稳定排序，词长优先，等长时保持出现顺序，保证同一问题每次检索顺序一致。
        candidates.sort((left, right) -> Integer.compare(right.length(), left.length()));
        return candidates;
    }

    /**
     * 收集一个连续串的词项。
     *
     * @param terms 词项集合
     * @param run   连续串缓冲
     * @author 余炘洋
     * @date 2026-10-19
     */
    private static void collect(Set<String> terms, StringBuilder run) {
        if (run.isEmpty()) {
            return;
        }
        String token = run.toString();
        run.setLength(0);
        if (containsCjk(token)) {
            // 中文串取滑窗二字词：中文无空格，整句做 LIKE 模式必然失配。
            for (int index = 0; index + MIN_TERM_LENGTH <= token.length(); index++) {
                String bigram = token.substring(index, index + MIN_TERM_LENGTH);
                // 说明：纯数字滑窗（如日期里的 2026）几乎没有检索价值，只放大查询次数。
                if (!isAllDigits(bigram)) {
                    terms.add(bigram);
                }
            }
            if (token.length() > MIN_TERM_LENGTH && token.length() <= 4) {
                terms.add(token);
            }
            return;
        }
        if (token.length() >= MIN_TERM_LENGTH) {
            terms.add(token.length() > MAX_TERM_LENGTH ? token.substring(0, MAX_TERM_LENGTH) : token);
        }
    }

    /**
     * 判断字符是否参与检索词。
     *
     * @param value 字符
     * @return 是否为字母、数字、下划线或中文
     * @author 余炘洋
     * @date 2026-10-19
     */
    private static boolean isSearchable(char value) {
        // 说明：下划线参与成词，保证 order_amount 这类业务标识符不被切碎。
        return Character.isLetterOrDigit(value) || value == '_' || containsCjk(value);
    }

    private static boolean containsCjk(String value) {
        for (int index = 0; index < value.length(); index++) {
            if (containsCjk(value.charAt(index))) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsCjk(char value) {
        return value >= '\u4e00' && value <= '\u9fff';
    }

    /**
     * 判断词项是否全部为数字。
     *
     * @param value 词项
     * @return 是否全部为数字
     * @author 余炘洋
     * @date 2026-10-19
     */
    private static boolean isAllDigits(String value) {
        for (int index = 0; index < value.length(); index++) {
            if (!Character.isDigit(value.charAt(index))) {
                return false;
            }
        }
        return !value.isEmpty();
    }
}
