package com.lingsql.chat.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
        String lastSeenId = resolveLastSeenId(resolvedTenantId, sql, data);
        ChatMemoryMessage userMessage = ChatMemoryMessage.user(maskSensitive(question));
        ChatMemoryMessage aiMessage = ChatMemoryMessage.assistant(maskSensitive(answer), maskSensitive(sql), lastSeenId);
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
        StringBuilder sb = new StringBuilder();
        for (String raw : rawMessages) {
            ChatMemoryMessage message = readMessage(raw);
            if (message == null || !StringUtils.hasText(message.getRole())) {
                continue;
            }
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
            if (StringUtils.hasText(message.getLastSeenId())) {
                sb.append("last_seen_id: ").append(message.getLastSeenId()).append("\n");
            }
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
     * 解析最近一次主键 ID。
     *
     * @param tenantId 租户标识
     * @param sql      SQL 文本
     * @param data     结果集
     * @return 主键 ID
     * @author 余炘洋
     * @date 2026-01-26
     */
    private String resolveLastSeenId(String tenantId, String sql, List<Map<String, Object>> data) {
        if (!StringUtils.hasText(sql) || data == null || data.isEmpty()) {
            return null;
        }
        Map<String, Object> firstRow = data.get(0);
        if (firstRow == null || firstRow.isEmpty()) {
            return null;
        }
        String mainTable = resolveMainTableName(sql);
        String primaryKey = resolvePrimaryKey(tenantId, mainTable);
        String value = resolveValueByColumn(firstRow, primaryKey);
        if (StringUtils.hasText(value)) {
            return value;
        }
        return resolveFallbackId(firstRow);
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
     * 解析回退 ID（id 或 _id）。
     *
     * @param row 数据行
     * @return ID 值
     * @author 余炘洋
     * @date 2026-01-26
     */
    private String resolveFallbackId(Map<String, Object> row) {
        if (row == null || row.isEmpty()) {
            return null;
        }
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            String key = normalizeKey(entry.getKey());
            if ("id".equalsIgnoreCase(key)) {
                String value = valueToString(entry.getValue());
                if (StringUtils.hasText(value)) {
                    return value;
                }
            }
        }
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            String key = normalizeKey(entry.getKey());
            if (key != null && key.toLowerCase(Locale.ROOT).endsWith("_id")) {
                String value = valueToString(entry.getValue());
                if (StringUtils.hasText(value)) {
                    return value;
                }
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
        return sessionInfo.getCombinedSessionId();
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
