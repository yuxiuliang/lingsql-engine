package com.lingsql.schema;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 批量字段注释的行解析。
 *
 * <p>这张表决定了「批量补注释」到底有没有生效：模型按「字段名=中文描述」逐行输出，
 * 解析不出来就等于所有字段都是「无描述」。演示环境用的是模型桩，返回内容不受控，
 * 真机只能走到降级分支——所以解析规则必须在这里钉住。</p>
 *
 * @author 余炘洋
 * @date 2026-10-26
 */
class SchemaScannerCommentParsingTest {

    @Test
    void shouldParsePlainLines() {
        Map<String, String> parsed = SchemaScanner.parseCommentLines("""
                id=主键编号
                amount=订单金额
                order_date=下单日期
                """);

        assertEquals("主键编号", parsed.get("id"));
        assertEquals("订单金额", parsed.get("amount"));
        assertEquals("下单日期", parsed.get("order_date"));
    }

    @Test
    void shouldTolerateBulletsAndNumbering() {
        Map<String, String> parsed = SchemaScanner.parseCommentLines("""
                - id = 主键
                * name=名称
                1. amount=金额
                """);

        assertEquals("主键", parsed.get("id"));
        assertEquals("名称", parsed.get("name"));
        assertEquals("金额", parsed.get("amount"));
    }

    @Test
    void shouldLowercaseKeysAndTrimValues() {
        Map<String, String> parsed = SchemaScanner.parseCommentLines("ORDER_NO =  订单号  ");

        assertEquals("订单号", parsed.get("order_no"));
        assertFalse(parsed.containsKey("ORDER_NO"));
    }

    @Test
    void shouldIgnoreNoiseAndMalformedLines() {
        Map<String, String> parsed = SchemaScanner.parseCommentLines("""
                好的，以下是字段说明：
                id=编号
                没有等号的一行
                =只有值没键
                amount=
                """);

        assertEquals(1, parsed.size());
        assertEquals("编号", parsed.get("id"));
    }

    @Test
    void shouldReturnEmptyOnBlankInput() {
        assertTrue(SchemaScanner.parseCommentLines("").isEmpty());
        assertTrue(SchemaScanner.parseCommentLines(null).isEmpty());
        assertTrue(SchemaScanner.parseCommentLines("   \n  ").isEmpty());
    }
}
