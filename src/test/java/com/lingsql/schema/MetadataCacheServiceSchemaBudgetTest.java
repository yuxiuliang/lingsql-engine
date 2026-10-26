package com.lingsql.schema;

import tools.jackson.databind.ObjectMapper;
import com.lingsql.config.LingSqlProperties;
import com.lingsql.metadata.service.MetadataExtService;
import com.lingsql.schema.model.TableInfo;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 表结构正文的体量上限。
 *
 * <p>背景：客户库有几百张表时，逐表逐字段全量拼进系统提示词会线性膨胀
 * （实测 60 张表 600 字段约 16.7 KB，外推 300 张表约 83.5 KB／2.4 万 token）。
 * 这里验证小库不受影响、大库被截断且**显式告知模型信息不完整**。</p>
 *
 * @author 余炘洋
 * @date 2026-10-26
 */
class MetadataCacheServiceSchemaBudgetTest {

    private MetadataCacheService newService(List<TableInfo> tables) {
        StringRedisTemplate redis = Mockito.mock(StringRedisTemplate.class);
        LingSqlProperties properties = new LingSqlProperties();
        MetadataExtService metadataExtService = Mockito.mock(MetadataExtService.class);
        Mockito.when(metadataExtService.mergeComments(Mockito.anyString(), Mockito.anyList()))
                .thenReturn(tables);
        return new MetadataCacheService(redis, new ObjectMapper(), properties, metadataExtService);
    }

    private TableInfo table(String name, int columns) {
        TableInfo table = new TableInfo();
        table.setTableName(name);
        table.setTableComment("测试表 " + name);
        List<TableInfo.ColumnInfo> cols = new ArrayList<>();
        for (int i = 0; i < columns; i++) {
            TableInfo.ColumnInfo col = new TableInfo.ColumnInfo();
            col.setColumnName("column_" + i);
            col.setDataType("VARCHAR");
            col.setColumnComment("字段说明 " + i);
            cols.add(col);
        }
        table.setColumns(cols);
        return table;
    }

    @Test
    void shouldRenderAllTablesWhenUnderBudget() {
        List<TableInfo> tables = List.of(table("demo_sales_order", 10), table("demo_product", 8));

        String schema = newService(tables).getAiFriendlySchema("T1", tables);

        assertTrue(schema.contains("Table: demo_sales_order"));
        assertTrue(schema.contains("Table: demo_product"));
        assertFalse(schema.contains("因篇幅限制"), "小库不应出现省略提示");
    }

    @Test
    void shouldOmitRemainingTablesAndSaySoWhenOverBudget() {
        // 每张表约 40 字段 × 约 45 字符 ≈ 1.8 KB，300 张远超 48 KB 预算
        List<TableInfo> tables = new ArrayList<>();
        for (int i = 0; i < 300; i++) {
            tables.add(table("fact_" + i + "_records", 40));
        }

        String schema = newService(tables).getAiFriendlySchema("T1", tables);

        assertTrue(schema.contains("因篇幅限制"), "超过预算必须显式说明已省略");
        assertTrue(schema.contains("仅列名"), "被省略的表应降级为表名列表");
        assertTrue(schema.contains("fact_299_records"), "省略提示里应能看到被省略的表名");
        assertTrue(schema.length() < 60_000,
                "截断后正文应显著小于原始全量，实际长度=" + schema.length());
        assertTrue(schema.contains("Table: fact_0_records"), "靠前的表仍应保留完整字段");
    }

    @Test
    void shouldReturnPlaceholderWhenNoTables() {
        String schema = newService(List.of()).getAiFriendlySchema("T1", List.of());

        assertTrue(schema.contains("暂无可用表结构"));
    }
}
