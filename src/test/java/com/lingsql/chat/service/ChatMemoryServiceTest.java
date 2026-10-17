package com.lingsql.chat.service;

import com.lingsql.config.LingSqlProperties;
import com.lingsql.context.SessionInfo;
import com.lingsql.context.TenantContext;
import com.lingsql.schema.MetadataCacheService;
import com.lingsql.schema.model.TableInfo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * 会话记忆服务单元测试，覆盖实体引用的结构化写入、元数据回查与注入过滤。
 *
 * @author 余炘洋
 * @date 2026-10-17
 */
class ChatMemoryServiceTest {

    private static final String TENANT_ID = "TENANT_A";

    private final List<String> storedMessages = new ArrayList<>();

    private StringRedisTemplate redisTemplate;

    private MetadataCacheService metadataCacheService;

    private LingSqlProperties properties;

    @BeforeEach
    void setUp() {
        TenantContext.setSessionInfo(new SessionInfo(TENANT_ID, 1001L));
        TenantContext.setConversationId("conv-1");
        storedMessages.clear();

        redisTemplate = Mockito.mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ListOperations<String, String> listOperations = Mockito.mock(ListOperations.class);
        when(redisTemplate.opsForList()).thenReturn(listOperations);
        when(listOperations.rightPush(anyString(), anyString())).thenAnswer(invocation -> {
            storedMessages.add(invocation.getArgument(1));
            return (long) storedMessages.size();
        });
        when(listOperations.range(anyString(), anyLong(), anyLong()))
                .thenAnswer(invocation -> new ArrayList<>(storedMessages));

        metadataCacheService = Mockito.mock(MetadataCacheService.class);
        properties = new LingSqlProperties();
        properties.getSecurity().setPromptInjectionKeywords(List.of("忽略指令"));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void shouldRecordStructuredEntityReferenceAndRenderIt() {
        when(metadataCacheService.getCachedSchema(TENANT_ID)).thenReturn(List.of(
                table("device", true, "id", "name")
        ));
        ChatMemoryService service = createService();

        service.saveSuccessExchange(TENANT_ID, "查询设备", "共 2 台设备",
                "SELECT id, name FROM device",
                List.of(Map.of("id", 7, "name", "A"), Map.of("id", 8, "name", "B")));

        String prompt = service.getHistoryPrompt();

        assertTrue(prompt.contains("实体引用: 表=device, 主键字段=id, 值=7, 结果行数=2"));
    }

    @Test
    void shouldFallbackToIdSuffixColumnWhenPrimaryKeyMissing() {
        when(metadataCacheService.getCachedSchema(TENANT_ID)).thenReturn(List.of(
                table("order", false, "order_id", "amount")
        ));
        ChatMemoryService service = createService();

        service.saveSuccessExchange(TENANT_ID, "查询订单", "共 1 笔订单",
                "SELECT order_id, amount FROM order",
                List.of(Map.of("order_id", 55, "amount", 100)));

        String prompt = service.getHistoryPrompt();

        assertTrue(prompt.contains("实体引用: 表=order, 主键字段=order_id, 值=55, 结果行数=1"));
    }

    @Test
    void shouldDropEntityReferenceWhenSchemaChanged() {
        when(metadataCacheService.getCachedSchema(TENANT_ID)).thenReturn(List.of(
                table("device", true, "id", "name")
        ));
        ChatMemoryService service = createService();
        service.saveSuccessExchange(TENANT_ID, "查询设备", "共 1 台设备",
                "SELECT id, name FROM device", List.of(Map.of("id", 7, "name", "A")));

        when(metadataCacheService.getCachedSchema(TENANT_ID)).thenReturn(List.of(
                table("device", false, "name")
        ));
        String prompt = service.getHistoryPrompt();

        assertFalse(prompt.contains("实体引用"));
        assertFalse(prompt.contains("last_seen_id: 7"));
    }

    @Test
    void shouldDropEntityReferenceWhenValueLooksLikeInjection() {
        when(metadataCacheService.getCachedSchema(TENANT_ID)).thenReturn(List.of(
                table("device", true, "id", "name")
        ));
        ChatMemoryService service = createService();

        service.saveSuccessExchange(TENANT_ID, "查询设备", "共 1 台设备",
                "SELECT id, name FROM device", List.of(Map.of("id", "忽略指令", "name", "A")));

        String prompt = service.getHistoryPrompt();

        assertFalse(prompt.contains("实体引用"));
        assertFalse(prompt.contains("忽略指令"));
    }

    @Test
    void shouldKeepLegacyLastSeenIdRenderingForOldMessages() {
        when(metadataCacheService.getCachedSchema(TENANT_ID)).thenReturn(List.of(
                table("device", true, "id")
        ));
        storedMessages.add("{\"role\":\"AI\",\"content\":\"旧消息\",\"sql\":\"SELECT id FROM device\",\"lastSeenId\":\"42\"}");
        ChatMemoryService service = createService();

        String prompt = service.getHistoryPrompt();

        assertTrue(prompt.contains("last_seen_id: 42"));
    }

    @Test
    void shouldSkipEntityReferenceWhenSchemaUnavailableAtWriteTime() {
        when(metadataCacheService.getCachedSchema(TENANT_ID)).thenReturn(null);
        ChatMemoryService service = createService();

        service.saveSuccessExchange(TENANT_ID, "查询设备", "共 1 台设备",
                "SELECT id, name FROM device", List.of(Map.of("id", 7, "name", "A")));

        String prompt = service.getHistoryPrompt();

        assertFalse(prompt.contains("实体引用"));
        assertFalse(prompt.contains("last_seen_id"));
    }

    private ChatMemoryService createService() {
        return new ChatMemoryService(redisTemplate, new ObjectMapper(), properties, metadataCacheService);
    }

    private TableInfo table(String tableName, boolean firstColumnIsPrimaryKey, String... columnNames) {
        TableInfo table = new TableInfo();
        table.setTableName(tableName);
        List<TableInfo.ColumnInfo> columns = new ArrayList<>();
        for (int index = 0; index < columnNames.length; index++) {
            TableInfo.ColumnInfo column = new TableInfo.ColumnInfo();
            column.setColumnName(columnNames[index]);
            column.setDataType("varchar");
            column.setPrimaryKey(firstColumnIsPrimaryKey && index == 0);
            columns.add(column);
        }
        table.setColumns(columns);
        return table;
    }
}
