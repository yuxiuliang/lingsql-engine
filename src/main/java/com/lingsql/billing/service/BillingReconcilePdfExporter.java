package com.lingsql.billing.service;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 计费对账 PDF 导出器：生成不可变的图文对账回执。
 *
 * <p>使用 OpenPDF（LGPL/MPL）而非 iText（AGPL），避免开源项目的许可证传染风险。</p>
 *
 * @author 余炘洋
 * @date 2026-10-05
 */
@Slf4j
@Component
public class BillingReconcilePdfExporter {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 导出计费对账 PDF。
     *
     * @param title   报告标题
     * @param headers 表头
     * @param rows    数据行
     * @return PDF 字节数组
     */
    public byte[] export(String title, List<String> headers, List<List<String>> rows) {
        if (headers == null || headers.isEmpty()) {
            throw new IllegalArgumentException("对账表头不能为空");
        }
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4);
            PdfWriter.getInstance(document, out);
            document.open();
            document.add(new Paragraph(title, new Font(Font.BOLD, 16)));
            document.add(new Paragraph("生成时间: " + LocalDateTime.now().format(DATE_TIME_FORMATTER)));
            document.add(new Paragraph(" "));

            PdfPTable table = new PdfPTable(headers.size());
            table.setWidthPercentage(100);
            for (String header : headers) {
                PdfPCell cell = new PdfPCell(new Phrase(header));
                cell.setHorizontalAlignment(Element.ALIGN_CENTER);
                table.addCell(cell);
            }
            if (rows != null) {
                for (List<String> row : rows) {
                    for (int i = 0; i < headers.size(); i++) {
                        table.addCell(i < row.size() && row.get(i) != null ? row.get(i) : "");
                    }
                }
            }
            document.add(table);
            document.close();
            return out.toByteArray();
        } catch (Exception exception) {
            log.error("对账 PDF 导出失败: title={}", title, exception);
            throw new IllegalStateException("对账 PDF 导出失败", exception);
        }
    }
}