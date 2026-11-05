package com.lingsql.schema;

import com.lingsql.schema.model.TableInfo;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 表相关性预筛的「选择质量」。
 *
 * <p>预筛唯一的真实风险是**该给的表没给**——那会直接让模型写出不可用的 SQL。
 * 这里用两类真实形态验证：</p>
 * <ul>
 *   <li>模拟既有规模夹具（60 张 <code>fact_NN_records</code>，表注释全空、字段名相同）——
 *       只能靠表名匹配，条件最苛刻；</li>
 *   <li>带中文表注释的业务库——按注释字面匹配。</li>
 * </ul>
 *
 * @author 余炘洋
 * @date 2026-10-26
 */
class SchemaTableSelectorQualityTest {

    private TableInfo fixtureTable(int index) {
        TableInfo table = new TableInfo();
        table.setTableName(String.format("fact_%02d_records", index));
        table.setTableComment("");
        List<TableInfo.ColumnInfo> cols = new ArrayList<>();
        for (String name : List.of("id", "order_no", "amount", "quantity", "order_date")) {
            TableInfo.ColumnInfo col = new TableInfo.ColumnInfo();
            col.setColumnName(name);
            col.setDataType("VARCHAR");
            col.setColumnComment("");
            cols.add(col);
        }
        table.setColumns(cols);
        return table;
    }

    private List<TableInfo> fixtureTables() {
        List<TableInfo> tables = new ArrayList<>();
        for (int i = 1; i <= 60; i++) {
            tables.add(fixtureTable(i));
        }
        return tables;
    }

    @Test
    void shouldSelectTableNamedInQuestionEvenWhenAllCommentsAreEmpty() {
        List<TableInfo> tables = fixtureTables();

        SchemaTableSelector.Selection selection =
                SchemaTableSelector.select("fact_07_records 里有多少行？", tables, 30);

        List<String> fullNames = selection.fullTables().stream().map(TableInfo::getTableName).toList();
        assertTrue(fullNames.contains("fact_07_records"),
                "问题点名的表必须展开，实际=" + fullNames);
        assertEquals(30, selection.fullTables().size());
        assertEquals(30, selection.nameOnlyTables().size());
    }

    @Test
    void shouldSelectEveryTableExplicitlyNamedInQuestion() {
        List<TableInfo> tables = fixtureTables();

        SchemaTableSelector.Selection selection = SchemaTableSelector.select(
                "请对比 fact_59_records 和 fact_03_records 的行数", tables, 30);

        List<String> fullNames = selection.fullTables().stream().map(TableInfo::getTableName).toList();
        assertTrue(fullNames.contains("fact_59_records"), "第一个点名的表必须展开，实际=" + fullNames);
        assertTrue(fullNames.contains("fact_03_records"), "第二个点名的表必须展开，实际=" + fullNames);
    }

    @Test
    void shouldMatchByChineseTableComment() {
        List<TableInfo> tables = new ArrayList<>();
        TableInfo order = new TableInfo();
        order.setTableName("demo_sales_order");
        order.setTableComment("销售订单主表");
        TableInfo.ColumnInfo amount = new TableInfo.ColumnInfo();
        amount.setColumnName("total_amount");
        amount.setColumnComment("订单金额");
        order.setColumns(List.of(amount));
        tables.add(order);
        for (int i = 0; i < 40; i++) {
            TableInfo other = new TableInfo();
            other.setTableName("unrelated_" + i);
            other.setTableComment("无关表" + i);
            other.setColumns(List.of());
            tables.add(other);
        }

        SchemaTableSelector.Selection selection =
                SchemaTableSelector.select("上个月的销售订单金额合计是多少？", tables, 10);

        List<String> fullNames = selection.fullTables().stream().map(TableInfo::getTableName).toList();
        assertTrue(fullNames.contains("demo_sales_order"), "按表注释应命中，实际=" + fullNames);
        assertEquals("demo_sales_order", fullNames.get(0), "命中的表应排在最前");
    }

    @Test
    void shouldNotDropMatchedTablesEvenWhenTheyExceedQuota() {
        // 60 张表全部能被问题命中（表名都含 fact_），配额 30 时必须仍然选出 30 张且不报错
        List<TableInfo> tables = fixtureTables();

        SchemaTableSelector.Selection selection =
                SchemaTableSelector.select("fact 表里哪张金额最大", tables, 30);

        assertEquals(30, selection.fullTables().size());
        assertEquals(30, selection.nameOnlyTables().size());
    }

    /**
     * 复刻现场：100 张 wide_NNN_records，注释全空，问题点名第 87 张。
     * 若失败，说明打分逻辑本身没匹配上表名。
     */
    @Test
    void shouldRankNamedTableFirstInWideFixture() {
        List<TableInfo> tables = new ArrayList<>();
        for (int i = 1; i <= 100; i++) {
            TableInfo t = new TableInfo();
            t.setTableName(String.format("wide_%03d_records", i));
            t.setTableComment("");
            TableInfo.ColumnInfo c = new TableInfo.ColumnInfo();
            c.setColumnName("id");
            c.setColumnComment("");
            t.setColumns(List.of(c));
            tables.add(t);
        }

        SchemaTableSelector.Selection selection =
                SchemaTableSelector.select("wide_087_records 里有多少行？", tables, 30);

        assertEquals("wide_087_records", selection.fullTables().get(0).getTableName(),
                "点名的表必须排第一，实际=" + selection.fullTables().get(0).getTableName());
    }
}
