package com.lingsql.audit.service;

import com.lingsql.audit.model.SqlExecuteLogView;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqlExecuteLogExcelExporterTest {

    @Test
    void exportShouldGenerateWorkbookAndEscapeFormulaLikeContent() throws Exception {
        SqlExecuteLogView view = new SqlExecuteLogView();
        view.setId(1L);
        view.setTenantId("TENANT_A");
        view.setUserId(1001L);
        view.setQuestion("=cmd|' /C calc'!A0");
        view.setAiSql("SELECT * FROM orders");
        view.setExecutedSql("SELECT * FROM orders LIMIT 10");
        view.setSuccess(1);
        view.setErrorMessage(null);
        view.setModelProvider("OPENAI");
        view.setModelName("gpt");
        view.setModelVersion("1.0");
        view.setTokenCount(123);
        view.setCostAmount(456L);
        view.setBillingVersion("v1");
        view.setExecuteCostMs(789L);
        view.setResultRows(10);
        view.setRequestSource("WEB");
        view.setRating(5);
        view.setComment("正常");
        view.setSlow(Boolean.FALSE);
        view.setCreateTime(LocalDateTime.of(2026, 4, 7, 22, 0, 0));

        SqlExecuteLogExcelExporter exporter = new SqlExecuteLogExcelExporter();
        byte[] bytes = exporter.export(List.of(view));

        assertTrue(bytes.length > 0);
        try (var workbook = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            var sheet = workbook.getSheetAt(0);
            assertEquals("SQL日志", sheet.getSheetName());
            assertEquals("原始问题", sheet.getRow(0).getCell(3).getStringCellValue());
            assertEquals("'=cmd|' /C calc'!A0", sheet.getRow(1).getCell(3).getStringCellValue());
            assertEquals("成功", sheet.getRow(1).getCell(6).getStringCellValue());
            assertEquals("2026-04-07 22:00:00", sheet.getRow(1).getCell(20).getStringCellValue());
        }
    }
}
