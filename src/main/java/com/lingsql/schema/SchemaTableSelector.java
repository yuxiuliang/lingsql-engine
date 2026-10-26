package com.lingsql.schema;

import com.lingsql.schema.model.TableInfo;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 按问题挑选「需要展开字段」的表。
 *
 * <p>背景：单租户白名单最多 100 张表，而系统提示词里表结构是逐表逐字段全量拼进去的。
 * 100 张 × 25 字段约 116 KB（约 3.3 万 token），既是成本也是干扰。这里先用问题与
 * 表名／表注释／字段名／字段注释的字面重合度粗筛，只把最相关的若干张表展开字段，
 * 其余表在提示词里只保留表名——既压缩体量，也减少模型被无关表干扰。</p>
 *
 * <p>选择刻意保守：表数不超过阈值时原样返回；字面匹配不到任何表时也按原顺序补齐，
 * 不做「选不出来就少给」这种会直接降低正确率的处理。</p>
 *
 * @author 余炘洋
 * @date 2026-10-26
 */
public final class SchemaTableSelector {

    /**
     * 超过该表数才启用筛选。
     */
    public static final int DEFAULT_MAX_FULL_TABLES = 30;

    private SchemaTableSelector() {
    }

    /**
     * 筛选结果。
     *
     * @param fullTables     需要展开字段的表（保持原始顺序）
     * @param nameOnlyTables 只保留表名的表
     */
    public record Selection(List<TableInfo> fullTables, List<TableInfo> nameOnlyTables) {
    }

    /**
     * 按问题挑选需要展开字段的表。
     *
     * @param question 用户问题
     * @param tables   全部可用表
     * @param maxFull  最多展开多少张表
     * @return 筛选结果
     * @author 余炘洋
     * @date 2026-10-26
     */
    public static Selection select(String question, List<TableInfo> tables, int maxFull) {
        if (tables == null || tables.isEmpty()) {
            return new Selection(List.of(), List.of());
        }
        if (maxFull <= 0 || tables.size() <= maxFull) {
            return new Selection(tables, List.of());
        }
        Set<String> terms = extractTerms(question);
        List<int[]> scored = new ArrayList<>();
        for (int i = 0; i < tables.size(); i++) {
            scored.add(new int[]{i, terms.isEmpty() ? 0 : score(tables.get(i), terms)});
        }
        // 相关度降序，相关度相同则保持原始顺序
        scored.sort((a, b) -> b[1] != a[1] ? Integer.compare(b[1], a[1]) : Integer.compare(a[0], b[0]));
        boolean[] chosen = new boolean[tables.size()];
        int picked = 0;
        for (int[] item : scored) {
            if (picked >= maxFull) {
                break;
            }
            chosen[item[0]] = true;
            picked++;
        }
        List<TableInfo> full = new ArrayList<>();
        List<TableInfo> nameOnly = new ArrayList<>();
        for (int i = 0; i < tables.size(); i++) {
            if (chosen[i]) {
                full.add(tables.get(i));
            } else {
                nameOnly.add(tables.get(i));
            }
        }
        return new Selection(full, nameOnly);
    }

    /**
     * 从问题里抽取匹配用的词项：英文/数字按分隔符切词（长度 ≥ 3），
     * 中文按相邻两字滑窗（避免依赖分词器）。
     */
    private static Set<String> extractTerms(String question) {
        Set<String> terms = new LinkedHashSet<>();
        if (question == null || question.isBlank()) {
            return terms;
        }
        String normalized = question.toLowerCase(Locale.ROOT);
        for (String token : normalized.split("[^\\p{IsHan}a-z0-9_]+")) {
            if (token.isEmpty()) {
                continue;
            }
            if (token.codePoints().allMatch(cp -> Character.UnicodeScript.of(cp) == Character.UnicodeScript.HAN)) {
                if (token.length() >= 2) {
                    for (int i = 0; i + 2 <= token.length(); i++) {
                        terms.add(token.substring(i, i + 2));
                    }
                } else {
                    terms.add(token);
                }
            } else if (token.length() >= 3) {
                terms.add(token);
            }
        }
        return terms;
    }

    private static int score(TableInfo table, Set<String> terms) {
        StringBuilder haystack = new StringBuilder();
        haystack.append(table.getTableName() == null ? "" : table.getTableName().toLowerCase(Locale.ROOT)).append(' ')
                .append(table.getTableComment() == null ? "" : table.getTableComment().toLowerCase(Locale.ROOT));
        if (table.getColumns() != null) {
            for (TableInfo.ColumnInfo col : table.getColumns()) {
                haystack.append(' ')
                        .append(col.getColumnName() == null ? "" : col.getColumnName().toLowerCase(Locale.ROOT))
                        .append(' ')
                        .append(col.getColumnComment() == null ? "" : col.getColumnComment().toLowerCase(Locale.ROOT));
            }
        }
        String text = haystack.toString();
        int hits = 0;
        for (String term : terms) {
            if (text.contains(term)) {
                hits++;
            }
        }
        return hits;
    }
}
