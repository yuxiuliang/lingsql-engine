package com.lingsql.executor;

import com.lingsql.schema.MetadataCacheService;
import com.lingsql.schema.model.TableInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.util.TablesNamesFinder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Schema 回查服务：表或字段失配时，针对失败 SQL 回查当前租户元数据，生成下一轮的定向纠正观察。
 *
 * <p>回查只读取当前租户的 Schema 缓存，不直接访问业务库，也不改变安全查询模块的执行顺序；
 * 输出文本会经过标识符字符集校验与长度限制，只用于下一轮 SQL 生成提示。</p>
 *
 * @author 余炘洋
 * @date 2026-10-17
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SchemaBackCheckService {

    /**
     * 单条回查观察的最大长度，避免放大提示词。
     */
    private static final int MAX_OBSERVATION_LENGTH = 1200;

    /**
     * 最多回查的表数量。
     */
    private static final int MAX_TABLES = 6;

    /**
     * 每张已知表最多列出的字段数量。
     */
    private static final int MAX_COLUMNS_PER_TABLE = 12;

    /**
     * 每个缺失标识符最多给出的相近候选数量。
     */
    private static final int MAX_SUGGESTIONS = 3;

    /**
     * 从数据库错误信息中最多提取的标识符数量。
     */
    private static final int MAX_REPORTED_IDENTIFIERS = 2;

    /**
     * 受控标识符格式：只允许库名、表名、字段名的安全字符集。
     */
    private static final Pattern IDENTIFIER_PATTERN = Pattern.compile("^[A-Za-z0-9_$.]{1,64}$");

    /**
     * 数据库错误信息中的单引号标识符。
     */
    private static final Pattern QUOTED_IDENTIFIER_PATTERN = Pattern.compile("'([^']{1,64})'");

    private static final String OBSERVATION_PREFIX = "Schema 回查：";

    private static final String OBSERVATION_SUFFIX = "请仅使用上述表和字段重新生成 SQL；如需访问新表，请先完成 Schema 同步。";

    private static final String NO_MISMATCH_NOTE =
            "回查最新元数据后未发现缺失的表或字段，请检查 Join、别名与筛选条件是否与元数据一致。";

    private final MetadataCacheService metadataCacheService;

    /**
     * 回查失败 SQL 引用的表与字段，生成下一轮的定向纠正观察。
     *
     * @param tenantId          租户标识
     * @param failedSql         上一轮失败 SQL
     * @param errorMessage      数据库错误信息
     * @param fallbackObservation 无法回查时使用的通用观察
     * @return 定向观察文本
     * @author 余炘洋
     * @date 2026-10-17
     */
    public String describeMismatch(String tenantId, String failedSql, String errorMessage, String fallbackObservation) {
        List<TableInfo> tables = metadataCacheService.getCachedSchema(tenantId);
        if (tables == null || tables.isEmpty()) {
            return fallbackObservation;
        }
        List<String> referencedTables = resolveReferencedTables(failedSql);
        if (referencedTables.isEmpty()) {
            return fallbackObservation;
        }
        Map<String, TableInfo> schemaIndex = indexByName(tables);
        List<String> tableNames = new ArrayList<>(schemaIndex.keySet());

        List<String> missingTables = new ArrayList<>();
        List<TableInfo> knownTables = new ArrayList<>();
        for (String table : referencedTables) {
            TableInfo matched = schemaIndex.get(lastSegment(table).toLowerCase(Locale.ROOT));
            if (matched == null) {
                missingTables.add(table);
                continue;
            }
            if (knownTables.size() < MAX_TABLES) {
                knownTables.add(matched);
            }
        }

        boolean flagged = false;
        StringBuilder observation = new StringBuilder(OBSERVATION_PREFIX);
        for (String missingTable : missingTables) {
            flagged = true;
            observation.append("\n- 表 ").append(missingTable).append(" 在当前元数据中不存在");
            List<String> suggestions = suggest(lastSegment(missingTable), tableNames);
            if (!suggestions.isEmpty()) {
                observation.append("，相近候选：").append(String.join("、", suggestions));
            }
            observation.append("。");
        }
        for (TableInfo table : knownTables) {
            observation.append("\n- ").append(describeColumns(table));
        }
        for (String reported : resolveReportedIdentifiers(errorMessage)) {
            String normalized = lastSegment(reported).toLowerCase(Locale.ROOT);
            if (missingTables.stream().map(SchemaBackCheckService::lastSegment)
                    .map(name -> name.toLowerCase(Locale.ROOT))
                    .anyMatch(normalized::equals)) {
                continue;
            }
            flagged = true;
            TableInfo owner = findColumnOwner(knownTables, normalized);
            if (owner != null) {
                observation.append("\n- 数据库报告的缺失标识符 ").append(reported)
                        .append(" 已存在于最新元数据（表 ").append(owner.getTableName())
                        .append("），请检查表限定与别名是否正确。");
                continue;
            }
            observation.append("\n- 数据库报告的缺失标识符 ").append(reported).append(" 不在上述字段中");
            List<String> suggestions = suggestColumns(knownTables, normalized);
            if (!suggestions.isEmpty()) {
                observation.append("，相近字段候选：").append(String.join("、", suggestions));
            }
            observation.append("。");
        }
        if (!flagged) {
            observation.append("\n- ").append(NO_MISMATCH_NOTE);
        }
        observation.append("\n").append(OBSERVATION_SUFFIX);
        return truncate(observation.toString());
    }

    /**
     * 解析失败 SQL 引用的表名列表。
     *
     * @param failedSql 失败 SQL
     * @return 表名列表
     * @author 余炘洋
     * @date 2026-10-17
     */
    private List<String> resolveReferencedTables(String failedSql) {
        if (!StringUtils.hasText(failedSql)) {
            return List.of();
        }
        try {
            Statement statement = CCJSqlParserUtil.parse(failedSql);
            Set<String> tables = new LinkedHashSet<>(new TablesNamesFinder().getTables(statement));
            List<String> result = new ArrayList<>();
            for (String table : tables) {
                if (result.size() >= MAX_TABLES) {
                    break;
                }
                if (StringUtils.hasText(table) && IDENTIFIER_PATTERN.matcher(table).matches()) {
                    result.add(table);
                }
            }
            return result;
        } catch (Exception exception) {
            log.debug("Schema 回查解析失败 SQL 未成功: {}", exception.getMessage());
            return List.of();
        }
    }

    /**
     * 从数据库错误信息中提取受控标识符。
     *
     * @param errorMessage 数据库错误信息
     * @return 标识符列表
     * @author 余炘洋
     * @date 2026-10-17
     */
    private List<String> resolveReportedIdentifiers(String errorMessage) {
        if (!StringUtils.hasText(errorMessage)) {
            return List.of();
        }
        Matcher matcher = QUOTED_IDENTIFIER_PATTERN.matcher(errorMessage);
        List<String> identifiers = new ArrayList<>();
        while (matcher.find() && identifiers.size() < MAX_REPORTED_IDENTIFIERS) {
            String candidate = matcher.group(1);
            if (IDENTIFIER_PATTERN.matcher(candidate).matches() && !identifiers.contains(candidate)) {
                identifiers.add(candidate);
            }
        }
        return identifiers;
    }

    /**
     * 构建已知表的字段说明。
     *
     * @param table 表结构
     * @return 字段说明
     * @author 余炘洋
     * @date 2026-10-17
     */
    private String describeColumns(TableInfo table) {
        List<TableInfo.ColumnInfo> columns = table.getColumns() == null ? List.of() : table.getColumns();
        StringBuilder sb = new StringBuilder("表 ").append(table.getTableName())
                .append(" 的可用字段（共 ").append(columns.size()).append(" 个）：");
        int listed = 0;
        for (TableInfo.ColumnInfo column : columns) {
            if (column == null || !StringUtils.hasText(column.getColumnName())) {
                continue;
            }
            if (listed >= MAX_COLUMNS_PER_TABLE) {
                sb.append(" 等");
                break;
            }
            if (listed > 0) {
                sb.append("、");
            }
            sb.append(column.getColumnName()).append(column.isPrimaryKey() ? "[主键]" : "");
            listed++;
        }
        sb.append("。");
        return sb.toString();
    }

    /**
     * 在已知表中查找字段归属。
     *
     * @param tables 已知表
     * @param column 字段名（小写）
     * @return 归属表，未找到时返回 null
     * @author 余炘洋
     * @date 2026-10-17
     */
    private TableInfo findColumnOwner(List<TableInfo> tables, String column) {
        for (TableInfo table : tables) {
            if (table.getColumns() == null) {
                continue;
            }
            for (TableInfo.ColumnInfo info : table.getColumns()) {
                if (info != null && StringUtils.hasText(info.getColumnName())
                        && info.getColumnName().toLowerCase(Locale.ROOT).equals(column)) {
                    return table;
                }
            }
        }
        return null;
    }

    /**
     * 在已知表字段中给出相近候选。
     *
     * @param tables 已知表
     * @param column 字段名（小写）
     * @return 候选列表
     * @author 余炘洋
     * @date 2026-10-17
     */
    private List<String> suggestColumns(List<TableInfo> tables, String column) {
        List<String> candidates = new ArrayList<>();
        for (TableInfo table : tables) {
            if (table.getColumns() == null) {
                continue;
            }
            for (TableInfo.ColumnInfo info : table.getColumns()) {
                if (info != null && StringUtils.hasText(info.getColumnName())) {
                    candidates.add(table.getTableName() + "." + info.getColumnName());
                }
            }
        }
        return suggest(column, candidates);
    }

    /**
     * 按相近程度给出候选标识符。
     *
     * @param token      缺失标识符
     * @param candidates 候选标识符
     * @return 相近候选
     * @author 余炘洋
     * @date 2026-10-17
     */
    private List<String> suggest(String token, List<String> candidates) {
        if (!StringUtils.hasText(token) || candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        String normalizedToken = lastSegment(token).toLowerCase(Locale.ROOT);
        Map<String, Integer> scored = new LinkedHashMap<>();
        for (String candidate : candidates) {
            if (!StringUtils.hasText(candidate)) {
                continue;
            }
            String normalized = lastSegment(candidate).toLowerCase(Locale.ROOT);
            int rank = similarityRank(normalizedToken, normalized);
            if (rank >= 0) {
                scored.merge(candidate, rank, Math::min);
            }
        }
        return scored.entrySet().stream()
                .sorted(Comparator.comparingInt(Map.Entry<String, Integer>::getValue)
                        .thenComparing(Map.Entry::getKey))
                .limit(MAX_SUGGESTIONS)
                .map(Map.Entry::getKey)
                .toList();
    }

    /**
     * 计算相似度排名，越小越相近，-1 表示不相近。
     *
     * @param token     缺失标识符（小写）
     * @param candidate 候选标识符（小写）
     * @return 排名
     * @author 余炘洋
     * @date 2026-10-17
     */
    private int similarityRank(String token, String candidate) {
        if (token.equals(candidate)) {
            return 0;
        }
        if (token.contains(candidate) || candidate.contains(token)) {
            return 1;
        }
        int distance = levenshtein(token, candidate);
        if (distance <= 2) {
            return 2 + distance;
        }
        int sharedPrefix = 0;
        int limit = Math.min(token.length(), candidate.length());
        while (sharedPrefix < limit && token.charAt(sharedPrefix) == candidate.charAt(sharedPrefix)) {
            sharedPrefix++;
        }
        if (sharedPrefix >= 3) {
            return 5;
        }
        return -1;
    }

    /**
     * 计算编辑距离。
     *
     * @param left  左文本
     * @param right 右文本
     * @return 编辑距离
     * @author 余炘洋
     * @date 2026-10-17
     */
    private int levenshtein(String left, String right) {
        int[] previous = new int[right.length() + 1];
        int[] current = new int[right.length() + 1];
        for (int j = 0; j <= right.length(); j++) {
            previous[j] = j;
        }
        for (int i = 1; i <= left.length(); i++) {
            current[0] = i;
            for (int j = 1; j <= right.length(); j++) {
                int replaceCost = previous[j - 1] + (left.charAt(i - 1) == right.charAt(j - 1) ? 0 : 1);
                current[j] = Math.min(Math.min(previous[j] + 1, current[j - 1] + 1), replaceCost);
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[right.length()];
    }

    /**
     * 按名称索引表结构。
     *
     * @param tables 表结构列表
     * @return 索引
     * @author 余炘洋
     * @date 2026-10-17
     */
    private Map<String, TableInfo> indexByName(List<TableInfo> tables) {
        Map<String, TableInfo> index = new LinkedHashMap<>();
        for (TableInfo table : tables) {
            if (table == null || !StringUtils.hasText(table.getTableName())) {
                continue;
            }
            index.putIfAbsent(lastSegment(table.getTableName()).toLowerCase(Locale.ROOT), table);
        }
        return index;
    }

    /**
     * 截取标识符最后一段，兼容库名限定。
     *
     * @param identifier 标识符
     * @return 最后一段
     * @author 余炘洋
     * @date 2026-10-17
     */
    private static String lastSegment(String identifier) {
        if (!StringUtils.hasText(identifier)) {
            return "";
        }
        int dotIndex = identifier.lastIndexOf('.');
        return dotIndex > -1 ? identifier.substring(dotIndex + 1) : identifier;
    }

    /**
     * 限制观察文本长度。
     *
     * @param text 观察文本
     * @return 受控文本
     * @author 余炘洋
     * @date 2026-10-17
     */
    private String truncate(String text) {
        if (text.length() <= MAX_OBSERVATION_LENGTH) {
            return text;
        }
        return text.substring(0, MAX_OBSERVATION_LENGTH - 1) + "…";
    }
}
