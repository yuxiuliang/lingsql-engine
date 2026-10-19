package com.lingsql.billing.model;

import java.util.List;
import java.util.Map;

/**
 * 供应商账单导入报告。
 *
 * <p>导入采用「逐行校验、合法行落库、非法行拒绝」策略：计数为权威结果，
 * {@code rows} 仅保留前 {@code VendorBillImportService.MAX_REPORT_ROWS} 行明细以限制响应体积。</p>
 *
 * @param csvSha256      CSV 文本的 SHA-256 摘要，用于审计追溯
 * @param totalRows      数据行总数（不含表头、空行与注释行）
 * @param importedCount  成功导入行数
 * @param duplicateCount 重复行数
 * @param rejectedCount  拒绝行数
 * @param matchSummary   匹配状态汇总（状态名 -> 行数），仅统计成功导入行
 * @param rows           逐行明细（有上限）
 * @author 余炘洋
 * @date 2026-10-17
 */
public record VendorBillImportReport(String csvSha256,
                                     int totalRows,
                                     int importedCount,
                                     int duplicateCount,
                                     int rejectedCount,
                                     Map<String, Integer> matchSummary,
                                     List<VendorBillImportRow> rows) {
}
