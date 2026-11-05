package com.lingsql.schema;

import com.lingsql.schema.model.TableInfo;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 表相关性粗筛的行为约束。
 *
 * <p>这是「少给表」的地方，选错会直接降低正确率，因此测试重点不是"选出最好的"，
 * 而是"不该少给的时候绝不少给"。</p>
 *
 * @author 余炘洋
 * @date 2026-10-26
 */
class SchemaTableSelectorTest {

    private TableInfo table(String name, String comment, String... columnComments) {
        TableInfo table = new TableInfo();
        table.setTableName(name);
        table.setTableComment(comment);
        List<TableInfo.ColumnInfo> cols = new ArrayList<>();
        for (int i = 0; i < columnComments.length; i++) {
            TableInfo.ColumnInfo col = new TableInfo.ColumnInfo();
            col.setColumnName("col_" + i);
            col.setDataType("VARCHAR");
            col.setColumnComment(columnComments[i]);
            cols.add(col);
        }
        table.setColumns(cols);
        return table;
    }

    @Test
    void shouldKeepAllTablesWhenUnderThreshold() {
        List<TableInfo> tables = List.of(table("t1", "订单"), table("t2", "商品"), table("t3", "库存"));

        SchemaTableSelector.Selection selection = SchemaTableSelector.select("昨天销售额", tables, 30);

        assertEquals(3, selection.fullTables().size());
        assertTrue(selection.nameOnlyTables().isEmpty(), "表数不超阈值时不得降级任何表");
    }

    @Test
    void shouldExpandRelevantTablesAndNameOnlyTheRest() {
        List<TableInfo> tables = new ArrayList<>();
        tables.add(table("demo_sales_order", "销售订单", "订单号", "下单日期", "订单金额"));
        tables.add(table("demo_product", "商品档案", "商品名称", "单价"));
        for (int i = 0; i < 40; i++) {
            tables.add(table("unrelated_" + i, "无关表" + i, "字段甲", "字段乙"));
        }

        SchemaTableSelector.Selection selection =
                SchemaTableSelector.select("2026年9月已完成的销售订单金额是多少？", tables, 5);

        assertEquals(5, selection.fullTables().size());
        assertEquals(tables.size() - 5, selection.nameOnlyTables().size());
        List<String> fullNames = selection.fullTables().stream().map(TableInfo::getTableName).toList();
        assertTrue(fullNames.contains("demo_sales_order"), "与问题字面重合的表必须被展开，实际=" + fullNames);
    }

    @Test
    void shouldStillFillQuotaWhenNothingMatches() {
        List<TableInfo> tables = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            tables.add(table("zzz_" + i, "表" + i, "甲", "乙"));
        }

        SchemaTableSelector.Selection selection =
                SchemaTableSelector.select("完全不相干的问题 xyzzy", tables, 8);

        assertEquals(8, selection.fullTables().size(), "匹配不到时也要按原顺序补满配额，不能少给");
        assertEquals("zzz_0", selection.fullTables().get(0).getTableName(), "补满时应保持原始顺序");
        assertEquals(12, selection.nameOnlyTables().size());
    }

    @Test
    void shouldNotFilterWhenQuestionBlank() {
        List<TableInfo> tables = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            tables.add(table("t_" + i, "表" + i, "字段"));
        }

        SchemaTableSelector.Selection selection = SchemaTableSelector.select("   ", tables, 5);

        assertEquals(5, selection.fullTables().size());
        assertFalse(selection.fullTables().isEmpty());
    }
}
