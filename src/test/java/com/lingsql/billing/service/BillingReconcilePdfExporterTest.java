package com.lingsql.billing.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 对账 PDF 导出器单元测试。
 *
 * @author 余炘洋
 * @date 2026-10-05
 */
class BillingReconcilePdfExporterTest {

    private final BillingReconcilePdfExporter exporter = new BillingReconcilePdfExporter();

    @Test
    void exportProducesPdfMagicHeader() {
        byte[] pdf = exporter.export("计费对账单", List.of("事件ID", "金额"), List.of(List.of("evt-1", "1230")));
        assertTrue(pdf.length > 0);
        assertEquals('%', (char) pdf[0]);
        assertEquals('P', (char) pdf[1]);
        assertEquals('D', (char) pdf[2]);
        assertEquals('F', (char) pdf[3]);
    }

    @Test
    void exportHandlesRaggedRows() {
        byte[] pdf = exporter.export("对账", List.of("A", "B", "C"), List.of(List.of("only-one")));
        assertTrue(pdf.length > 0);
    }

    @Test
    void exportRejectsEmptyHeaders() {
        assertThrows(IllegalArgumentException.class, () -> exporter.export("对账", List.of(), List.of()));
    }
}