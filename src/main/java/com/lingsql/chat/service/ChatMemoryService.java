package com.lingsql.chat.service;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.lingsql.chat.model.ChatEntityReference;
import com.lingsql.chat.model.ChatMemoryMessage;
import com.lingsql.config.LingSqlProperties;
import com.lingsql.context.SessionInfo;
import com.lingsql.context.TenantContext;
import com.lingsql.schema.MetadataCacheService;
import com.lingsql.schema.model.TableInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 会话记忆服务，负责按用户隔离存储对话上下文。
 *
 * @author 余炘洋
 * @date 2026-01-26
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatMemoryService {

    private static final String MEMORY_KEY_PREFIX = "lingsql:chat:memory:";

    /**
     * 实体引用值的最大长度。
     */
    private static final int MAX_ENTITY_VALUE_LENGTH = 64;

    /**
     * 受控标识符格式：只允许表名与字段名的安全字符集。
     */
    private static final Pattern IDENTIFIER_PATTERN = Pattern.compile("^[A-Za-z0-9_$]{1,64}$");

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final LingSqlProperties properties;
    private final MetadataCacheService metadataCacheService;

    /**
     * 保存单条对话消息。
     *
     * @param role    角色
     * @param content 内容
     * @author 余炘洋
     * @date 2026-01-26
     */
    public void saveMessage(String role, String content) {
        SessionInfo sessionInfo = TenantContext.getSessionInfo();
        String combinedSessionId = resolveCombinedSessionId(sessionInfo);
        if (!StringUtils.hasText(combinedSessionId) || !StringUtils.hasText(content)) {
            return;
        }
        ChatMemoryMessage message = new ChatMemoryMessage();
        message.setRole(role);
        message.setContent(maskSensitive(content));
        pushMessage(combinedSessionId, message);
    }

    /**
     * 保存成功的对话上下文。
     *
     * @param tenantId 租户标识
     * @param question 用户问题
     * @param answer   AI 答复
     * @param sql      成功执行 SQL
     * @param data     查询结果
     * @author 余炘洋
     * @date 2026-01-26
     */
    public void saveSuccessExchange(String tenantId, String question, String answer, String sql, List<Map<String, Object>> data) {
        SessionInfo sessionInfo = TenantContext.getSessionInfo();
        String combinedSessionId = resolveCombinedSessionId(sessionInfo);
        if (!StringUtils.hasText(combinedSessionId)) {
            return;
        }
        if (!StringUtils.hasText(question) || !StringUtils.hasText(answer)) {
            return;
        }
        if (!StringUtils.hasText(sql)) {
            return;
        }
        String resolvedTenantId = StringUtils.hasText(tenantId) ? tenantId : sessionInfo.tenantId();
        ChatEntityReference reference = resolveEntityReference(resolvedTenantId, sql, data);
        ChatMemoryMessage userMessage = ChatMemoryMessage.user(maskSensitive(question));
        ChatMemoryMessage aiMessage = ChatMemoryMessage.assistant(maskSensitive(answer), maskSensitive(sql), reference);
        pushMessage(combinedSessionId, userMessage);
        pushMessage(combinedSessionId, aiMessage);
    }

    /**
     * 获取历史对话 Prompt。
     *
     * @return 历史 Prompt
     * @author 余炘洋
     * @date 2026-01-26
     */
    public String getHistoryPrompt() {
        SessionInfo sessionInfo = TenantContext.getSessionInfo();
        String combinedSessionId = resolveCombinedSessionId(sessionInfo);
        if (!StringUtils.hasText(combinedSessionId)) {
            return "";
        }
        int limit = resolveLimit();
        List<String> rawMessages = stringRedisTemplate.opsForList().range(buildKey(combinedSessionId), -limit, -1);
        if (rawMessages == null || rawMessages.isEmpty()) {
            return "";
        }
        List<ChatMemoryMessage> messages = new ArrayList<>();
        for (String raw : rawMessages) {
            ChatMemoryMessage message = readMessage(raw);
            if (message == null || !StringUtils.hasText(message.getRole())) {
                continue;
            }
            messages.add(message);
        }
        String tenantId = sessionInfo == null ? null : sessionInfo.tenantId();
        List<TableInfo> schemaSnapshot = resolveSchemaSnapshot(tenantId, messages);
        StringBuilder sb = new StringBuilder();
        for (ChatMemoryMessage message : messages) {
            String content = maskSensitive(message.getContent());
            if (!StringUtils.hasText(content)) {
                continue;
            }
            if ("User".equalsIgnoreCase(message.getRole())) {
                sb.append("User: ").append(content).append("\n");
                continue;
            }
            sb.append("AI: ").append(content).append("\n");
            if (StringUtils.hasText(message.getSql())) {
                sb.append("SQL: ").append(maskSensitive(message.getSql())).append("\n");
            }
            appendEntityReference(sb, tenantId, schemaSnapshot, message);
        }
        return sb.toString().trim();
    }

    /**
     * 写入消息并维护长度与 TTL。
     *
     * @param combinedSessionId 会话标识
     * @param message           消息
     * @author 余炘洋
     * @date 2026-01-26
     */
    private void pushMessage(String combinedSessionId, ChatMemoryMessage message) {
        String key = buildKey(combinedSessionId);
        try {
            String json = objectMapper.writeValueAsString(message);
            stringRedisTemplate.opsForList().rightPush(key, json);
            stringRedisTemplate.opsForList().trim(key, -resolveLimit(), -1);
            stringRedisTemplate.expire(key, resolveTtl());
        } catch (Exception ex) {
            log.warn("保存会话记忆失败: {}", ex.getMessage());
        }
    }

    /**
     * 解析最近一次结果中的实体引用。
     *
     * @param tenantId 租户标识
     * @param sql      SQL 文本
     * @param data     结果集
     * @return 实体引用，无法确认时返回 null
     * @author 余炘洋
     * @date 2026-10-17
     */
    private ChatEntityReference resolveEntityReference(String tenantId, String sql, List<Map<String, Object>> data) {
        if (!StringUtils.hasText(sql) || data == null || data.isEmpty()) {
            return null;
        }
        Map<String, Object> firstRow = data.get(0);
        if (firstRow == null || firstRow.isEmpty()) {
            return null;
        }
        String mainTable = resolveMainTableName(sql);
        String primaryKey = resolvePrimaryKey(tenantId, mainTable);
        String columnName = StringUtils.hasText(primaryKey) ? primaryKey : resolveFallbackColumn(firstRow);
        String value = sanitizeEntityValue(resolveValueByColumn(firstRow, columnName));
        if (!StringUtils.hasText(value) || !isEntityBoundToSchema(tenantId, mainTable, columnName)) {
            return null;
        }
        return new ChatEntityReference(mainTable, columnName, value, data.size());
    }

    /**
     * 写入前回查实体引用与当前元数据的绑定关系，无法确认时放弃引用。
     *
     * @param tenantId   租户标识
     * @param tableName  表名
     * @param columnName 字段名
     * @return 是否绑定成功
     * @author 余炘洋
     * @date 2026-10-17
     */
    private boolean isEntityBoundToSchema(String tenantId, String tableName, String columnName) {
        if (!StringUtils.hasText(tenantId) || !StringUtils.hasText(tableName) || !StringUtils.hasText(columnName)) {
            return false;
        }
        List<TableInfo> tables = metadataCacheService.getCachedSchema(tenantId);
        if (tables == null || tables.isEmpty()) {
            return false;
        }
        return isReferenceVisibleInSchema(tables, tableName, columnName);
    }

    /**
     * 按需加载 Schema 快照，用于读取侧实体引用回查。
     *
     * @param tenantId 租户标识
     * @param messages 会话消息
     * @return 表结构快照，无需回查时返回 null
     * @author 余炘洋
     * @date 2026-10-17
     */
    private List<TableInfo> resolveSchemaSnapshot(String tenantId, List<ChatMemoryMessage> messages) {
        if (!StringUtils.hasText(tenantId) || messages.stream().noneMatch(this::hasStructuredEntityReference)) {
            return null;
        }
        return metadataCacheService.getCachedSchema(tenantId);
    }

    /**
     * 判断消息是否带有结构化实体引用。
     *
     * @param message 消息
     * @return 是否带有结构化引用
     * @author 余炘洋
     * @date 2026-10-17
     */
    private boolean hasStructuredEntityReference(ChatMemoryMessage message) {
        return message != null
                && StringUtils.hasText(message.getLastSeenTable())
                && StringUtils.hasText(message.getLastSeenColumn())
                && StringUtils.hasText(message.getLastSeenId());
    }

    /**
     * 渲染实体引用：结构化引用需通过当前元数据回查，旧格式消息保留兼容渲染。
     *
     * @param sb             历史文本
     * @param tenantId       租户标识
     * @param schemaSnapshot 表结构快照
     * @param message        消息
     * @author 余炘洋
     * @date 2026-10-17
     */
    private void appendEntityReference(StringBuilder sb,
                                       String tenantId,
                                       List<TableInfo> schemaSnapshot,
                                       ChatMemoryMessage message) {
        String value = sanitizeEntityValue(message.getLastSeenId());
        if (!StringUtils.hasText(value)) {
            return;
        }
        String tableName = sanitizeIdentifier(message.getLastSeenTable());
        String columnName = sanitizeIdentifier(message.getLastSeenColumn());
        if (!StringUtils.hasText(tableName) || !StringUtils.hasText(columnName)) {
            sb.append("last_seen_id: ").append(value).append("\n");
            return;
        }
        if (!isReferenceVisibleInSchema(schemaSnapshot, tableName, columnName)) {
            log.debug("实体引用未通过元数据回查，已跳过: tenantId={}, table={}, column={}", tenantId, tableName, columnName);
            return;
        }
        int rowCount = message.getLastSeenRows() == null ? 1 : message.getLastSeenRows();
        sb.append("实体引用: 表=").append(tableName)
                .append(", 主键字段=").append(columnName)
                .append(", 值=").append(value)
                .append(", 结果行数=").append(rowCount)
                .append("\n");
    }

    /**
     * 回查实体引用是否仍存在于元数据快照；快照不可用时保留引用。
     *
     * @param schemaSnapshot 表结构快照
     * @param tableName      表名
     * @param columnName     字段名
     * @return 是否仍可见
     * @author 余炘洋
     * @date 2026-10-17
     */
    private boolean isReferenceVisibleInSchema(List<TableInfo> schemaSnapshot, String tableName, String columnName) {
        if (schemaSnapshot == null || schemaSnapshot.isEmpty()) {
            return true;
        }
        for (TableInfo table : schemaSnapshot) {
            if (table == null || !StringUtils.hasText(table.getTableName())
                    || !tableName.equalsIgnoreCase(table.getTableName())) {
                continue;
            }
            if (table.getColumns() == null) {
                return false;
            }
            for (TableInfo.ColumnInfo column : table.getColumns()) {
                if (column != null && StringUtils.hasText(column.getColumnName())
                        && columnName.equalsIgnoreCase(column.getColumnName())) {
                    return true;
                }
            }
            return false;
        }
        return false;
    }

    /**
     * 过滤实体引用值：去除控制字符、限制长度，并拒绝疑似注入内容。
     *
     * @param value 原始值
     * @return 受控值，不可用时返回 null
     * @author 余炘洋
     * @date 2026-10-17
     */
    private String sanitizeEntityValue(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String cleaned = value.replaceAll("[\\p{Cntrl}]+", " ").trim();
        if (!StringUtils.hasText(cleaned)) {
            return null;
        }
        if (cleaned.length() > MAX_ENTITY_VALUE_LENGTH) {
            cleaned = cleaned.substring(0, MAX_ENTITY_VALUE_LENGTH);
        }
        LingSqlProperties.Security security = properties.getSecurity();
        if (security != null && security.getPromptInjectionKeywords() != null) {
            String lower = cleaned.toLowerCase(Locale.ROOT);
            for (String keyword : security.getPromptInjectionKeywords()) {
                if (StringUtils.hasText(keyword) && lower.contains(keyword.toLowerCase(Locale.ROOT))) {
                    return null;
                }
            }
        }
        return cleaned;
    }

    /**
     * 校验标识符格式。
     *
     * @param identifier 标识符
     * @return 受控标识符，非法时返回 null
     * @author 余炘洋
     * @date 2026-10-17
     */
    private String sanitizeIdentifier(String identifier) {
        if (!StringUtils.hasText(identifier)) {
            return null;
        }
        String trimmed = identifier.trim();
        return IDENTIFIER_PATTERN.matcher(trimmed).matches() ? trimmed : null;
    }

    /**
     * 解析 SQL 主表名称。
     *
     * @param sql SQL 文本
     * @return 主表名称
     * @author 余炘洋
     * @date 2026-01-26
     */
    private String resolveMainTableName(String sql) {
        try {
            Select select = (Select) CCJSqlParserUtil.parse(sql);
            PlainSelect plainSelect = select.getPlainSelect();
            if (plainSelect != null && plainSelect.getFromItem() instanceof net.sf.jsqlparser.schema.Table table) {
                return table.getName();
            }
        } catch (Exception ex) {
            log.debug("解析主表失败: {}", ex.getMessage());
        }
        return null;
    }

    /**
     * 获取主表主键字段。
     *
     * @param tenantId  租户标识
     * @param tableName 表名
     * @return 主键字段
     * @author 余炘洋
     * @date 2026-01-26
     */
    private String resolvePrimaryKey(String tenantId, String tableName) {
        if (!StringUtils.hasText(tenantId) || !StringUtils.hasText(tableName)) {
            return null;
        }
        List<TableInfo> tables = metadataCacheService.getCachedSchema(tenantId);
        if (tables == null || tables.isEmpty()) {
            return null;
        }
        for (TableInfo table : tables) {
            if (table == null || !tableName.equalsIgnoreCase(table.getTableName())) {
                continue;
            }
            List<TableInfo.ColumnInfo> columns = table.getColumns();
            if (columns == null) {
                continue;
            }
            for (TableInfo.ColumnInfo column : columns) {
                if (column != null && column.isPrimaryKey()) {
                    return column.getColumnName();
                }
            }
        }
        return null;
    }

    /**
     * 根据列名解析值。
     *
     * @param row        数据行
     * @param columnName 列名
     * @return 字段值
     * @author 余炘洋
     * @date 2026-01-26
     */
    private String resolveValueByColumn(Map<String, Object> row, String columnName) {
        if (row == null || row.isEmpty() || !StringUtils.hasText(columnName)) {
            return null;
        }
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            String key = entry.getKey();
            if (!StringUtils.hasText(key)) {
                continue;
            }
            if (key.equalsIgnoreCase(columnName)) {
                return valueToString(entry.getValue());
            }
            int dotIndex = key.lastIndexOf('.');
            if (dotIndex > -1) {
                String suffix = key.substring(dotIndex + 1);
                if (suffix.equalsIgnoreCase(columnName)) {
                    return valueToString(entry.getValue());
                }
            }
        }
        return null;
    }

    /**
     * 解析回退主键字段（id 或 _id）。
     *
     * @param row 数据行
     * @return 字段名
     * @author 余炘洋
     * @date 2026-10-17
     */
    private String resolveFallbackColumn(Map<String, Object> row) {
        if (row == null || row.isEmpty()) {
            return null;
        }
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            String key = normalizeKey(entry.getKey());
            if ("id".equalsIgnoreCase(key) && StringUtils.hasText(valueToString(entry.getValue()))) {
                return key;
            }
        }
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            String key = normalizeKey(entry.getKey());
            if (key != null && key.toLowerCase(Locale.ROOT).endsWith("_id")
                    && StringUtils.hasText(valueToString(entry.getValue()))) {
                return key;
            }
        }
        return null;
    }

    /**
     * 构建 Redis Key。
     *
     * @param combinedSessionId 会话标识
     * @return Key
     * @author 余炘洋
     * @date 2026-01-26
     */
    private String buildKey(String combinedSessionId) {
        return MEMORY_KEY_PREFIX + combinedSessionId;
    }

    /**
     * 解析会话标识。
     *
     * @param sessionInfo 会话信息
     * @return 会话标识
     * @author 余炘洋
     * @date 2026-01-26
     */
    private String resolveCombinedSessionId(SessionInfo sessionInfo) {
        if (sessionInfo == null) {
            return null;
        }
        String combinedSessionId = sessionInfo.getCombinedSessionId();
        String conversationId = TenantContext.getConversationId();
        if (!StringUtils.hasText(conversationId)) {
            return combinedSessionId;
        }
        if (!StringUtils.hasText(combinedSessionId)) {
            return null;
        }
        return combinedSessionId + ":" + conversationId.trim();
    }

    /**
     * 标准化列名（去除表前缀）。
     *
     * @param key 原始列名
     * @return 标准化列名
     * @author 余炘洋
     * @date 2026-01-26
     */
    private String normalizeKey(String key) {
        if (!StringUtils.hasText(key)) {
            return key;
        }
        int dotIndex = key.lastIndexOf('.');
        return dotIndex > -1 ? key.substring(dotIndex + 1) : key;
    }

    /**
     * 解析缓存条数。
     *
     * @return 条数
     * @author 余炘洋
     * @date 2026-01-26
     */
    private int resolveLimit() {
        LingSqlProperties.Ai ai = properties.getAi();
        if (ai == null || ai.getMemory() == null) {
            return 10;
        }
        int limit = ai.getMemory().getLimit();
        return Math.max(1, limit);
    }

    /**
     * 解析缓存 TTL。
     *
     * @return TTL
     * @author 余炘洋
     * @date 2026-01-26
     */
    private Duration resolveTtl() {
        LingSqlProperties.Ai ai = properties.getAi();
        if (ai == null || ai.getMemory() == null || ai.getMemory().getTtl() == null) {
            return Duration.ofMinutes(30);
        }
        return ai.getMemory().getTtl();
    }

    /**
     * 过滤敏感词。
     *
     * @param text 原文
     * @return 脱敏文本
     * @author 余炘洋
     * @date 2026-01-26
     */
    private String maskSensitive(String text) {
        if (!StringUtils.hasText(text)) {
            return text;
        }
        LingSqlProperties.Security security = properties.getSecurity();
        if (security == null || security.getPromptInjectionKeywords() == null) {
            return text;
        }
        String result = text;
        for (String keyword : security.getPromptInjectionKeywords()) {
            if (!StringUtils.hasText(keyword)) {
                continue;
            }
            result = replaceIgnoreCase(result, keyword);
        }
        return result;
    }

    /**
     * 忽略大小写替换。
     *
     * @param text    原文
     * @param keyword 关键词
     * @return 替换结果
     * @author 余炘洋
     * @date 2026-01-26
     */
    private String replaceIgnoreCase(String text, String keyword) {
        Pattern pattern = Pattern.compile(Pattern.quote(keyword), Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String match = matcher.group();
            matcher.appendReplacement(sb, "*".repeat(match.length()));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /**
     * 解析 JSON 消息。
     *
     * @param raw JSON
     * @return 消息对象
     * @author 余炘洋
     * @date 2026-01-26
     */
    private ChatMemoryMessage readMessage(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        try {
            return objectMapper.readValue(raw, new TypeReference<ChatMemoryMessage>() {
            });
        } catch (Exception ex) {
            log.debug("解析记忆消息失败: {}", ex.getMessage());
            return null;
        }
    }

    /**
     * 转换值为字符串。
     *
     * @param value 值
     * @return 字符串
     * @author 余炘洋
     * @date 2026-01-26
     */
    private String valueToString(Object value) {
        if (value == null) {
            return null;
        }
        String text = Objects.toString(value, null);
        return StringUtils.hasText(text) ? text : null;
    }
}
