package com.lingsql.audit.service;

import com.lingsql.audit.model.SqlExecuteLogView;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * SQL 执行日志 Excel 导出器。
 *
 * @author 余炘洋
 * @date 2026-04-07
 */
@Slf4j
@Component
public class SqlExecuteLogExcelExporter {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int MAX_CELL_TEXT_LENGTH = 32767;
    private static final String SHEET_NAME = "SQL日志";

    /**
     * 将 SQL 执行日志导出为 Excel 二进制内容。
     *
     * @param logs 日志列表
     * @return Excel 字节数组
     * @author 余炘洋
     * @date 2026-04-07
     */
    public byte[] export(List<SqlExecuteLogView> logs) {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet(SHEET_NAME);
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle textStyle = createTextStyle(workbook);

            writeHeader(sheet, headerStyle);
            writeRows(sheet, textStyle, logs);
            applyColumnWidth(sheet);

            workbook.write(outputStream);
            return outputStream.toByteArray();
        } catch (IOException ex) {
            log.error("导出 SQL 日志 Excel 失败", ex);
            throw new IllegalStateException("导出 SQL 日志 Excel 失败", ex);
        }
    }

    private void writeHeader(Sheet sheet, CellStyle headerStyle) {
        String[] headers = {
                "日志ID",
                "租户ID",
                "用户ID",
                "原始问题",
                "AI生成SQL",
                "执行SQL",
                "执行状态",
                "错误信息",
                "模型供应商",
                "模型名称",
                "模型版本",
                "Token数量",
                "成本金额",
                "计费版本",
                "执行耗时(毫秒)",
                "结果行数",
                "请求来源",
                "评分",
                "评价内容",
                "是否慢查询",
                "创建时间"
        };
        var row = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = row.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }
    }

    private void writeRows(Sheet sheet, CellStyle textStyle, List<SqlExecuteLogView> logs) {
        if (logs == null || logs.isEmpty()) {
            return;
        }
        int rowIndex = 1;
        for (SqlExecuteLogView logView : logs) {
            var row = sheet.createRow(rowIndex++);
            int cellIndex = 0;
            writeCell(row.createCell(cellIndex++), logView == null ? null : logView.getId(), textStyle);
            writeCell(row.createCell(cellIndex++), logView == null ? null : logView.getTenantId(), textStyle);
            writeCell(row.createCell(cellIndex++), logView == null ? null : logView.getUserId(), textStyle);
            writeCell(row.createCell(cellIndex++), logView == null ? null : logView.getQuestion(), textStyle);
            writeCell(row.createCell(cellIndex++), logView == null ? null : logView.getAiSql(), textStyle);
            writeCell(row.createCell(cellIndex++), logView == null ? null : logView.getExecutedSql(), textStyle);
            writeCell(row.createCell(cellIndex++), resolveSuccessText(logView), textStyle);
            writeCell(row.createCell(cellIndex++), logView == null ? null : logView.getErrorMessage(), textStyle);
            writeCell(row.createCell(cellIndex++), logView == null ? null : logView.getModelProvider(), textStyle);
            writeCell(row.createCell(cellIndex++), logView == null ? null : logView.getModelName(), textStyle);
            writeCell(row.createCell(cellIndex++), logView == null ? null : logView.getModelVersion(), textStyle);
            writeCell(row.createCell(cellIndex++), logView == null ? null : logView.getTokenCount(), textStyle);
            writeCell(row.createCell(cellIndex++), logView == null ? null : logView.getCostAmount(), textStyle);
            writeCell(row.createCell(cellIndex++), logView == null ? null : logView.getBillingVersion(), textStyle);
            writeCell(row.createCell(cellIndex++), logView == null ? null : logView.getExecuteCostMs(), textStyle);
            writeCell(row.createCell(cellIndex++), logView == null ? null : logView.getResultRows(), textStyle);
            writeCell(row.createCell(cellIndex++), logView == null ? null : logView.getRequestSource(), textStyle);
            writeCell(row.createCell(cellIndex++), logView == null ? null : logView.getRating(), textStyle);
            writeCell(row.createCell(cellIndex++), logView == null ? null : logView.getComment(), textStyle);
            writeCell(row.createCell(cellIndex++), resolveSlowText(logView), textStyle);
            writeCell(row.createCell(cellIndex), formatDateTime(logView == null ? null : logView.getCreateTime()), textStyle);
        }
    }

    private void writeCell(Cell cell, Object value, CellStyle textStyle) {
        String text = value == null ? "" : String.valueOf(value);
        cell.setCellValue(sanitizeForExcel(text));
        cell.setCellStyle(textStyle);
    }

    private String resolveSuccessText(SqlExecuteLogView logView) {
        if (logView == null || logView.getSuccess() == null) {
            return "";
        }
        return logView.getSuccess() == 1 ? "成功" : "失败";
    }

    private String resolveSlowText(SqlExecuteLogView logView) {
        if (logView == null || logView.getSlow() == null) {
            return "";
        }
        return Boolean.TRUE.equals(logView.getSlow()) ? "是" : "否";
    }

    private String formatDateTime(LocalDateTime dateTime) {
        if (dateTime == null) {
            return "";
        }
        return DATE_TIME_FORMATTER.format(dateTime);
    }

    /**
     * 对导出文本执行 Excel 注入防护，并裁剪超长内容。
     *
     * @param text 原始文本
     * @return 安全文本
     * @author 余炘洋
     * @date 2026-04-07
     */
    private String sanitizeForExcel(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        String normalized = text.replace("\r\n", "\n").replace('\r', '\n');
        if (normalized.length() > MAX_CELL_TEXT_LENGTH) {
            normalized = normalized.substring(0, MAX_CELL_TEXT_LENGTH);
        }
        char firstChar = normalized.charAt(0);
        if (firstChar == '=' || firstChar == '+' || firstChar == '-' || firstChar == '@' || firstChar == '\t') {
            return "'" + normalized;
        }
        return normalized;
    }

    private CellStyle createHeaderStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setWrapText(true);
        return style;
    }

    private CellStyle createTextStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setAlignment(HorizontalAlignment.LEFT);
        style.setVerticalAlignment(VerticalAlignment.TOP);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setWrapText(true);
        return style;
    }

    private void applyColumnWidth(Sheet sheet) {
        int[] widths = {
                12, 18, 12, 36, 48, 48, 12, 28, 16, 16, 16, 12, 12, 16, 14, 12, 14, 10, 24, 12, 20
        };
        for (int i = 0; i < widths.length; i++) {
            sheet.setColumnWidth(i, widths[i] * 256);
        }
    }
}
