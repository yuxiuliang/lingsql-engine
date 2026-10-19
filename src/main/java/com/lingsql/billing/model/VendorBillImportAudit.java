package com.lingsql.billing.model;

import java.util.Map;

/**
 * 供应商账单导入审计载荷。
 *
 * <p>审计只记录 CSV 摘要与计数，不落原始账单明文。</p>
 *
 * @param csvSha256      CSV 文本的 SHA-256 摘要
 * @param csvLength      CSV 文本长度
 * @param dataRows       数据行总数，成功审计时填充
 * @param importedCount  成功导入行数，成功审计时填充
 * @param duplicateCount 重复行数，成功审计时填充
 * @param rejectedCount  拒绝行数，成功审计时填充
 * @param matchSummary   匹配状态汇总，成功审计时填充
 * @author 余炘洋
 * @date 2026-10-17
 */
public record VendorBillImportAudit(String csvSha256,
                                    int csvLength,
                                    int dataRows,
                                    int importedCount,
                                    int duplicateCount,
                                    int rejectedCount,
                                    Map<String, Integer> matchSummary) {
}
