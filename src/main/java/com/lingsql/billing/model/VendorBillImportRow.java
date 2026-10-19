package com.lingsql.billing.model;

/**
 * 供应商账单导入逐行结果。
 *
 * @param lineNumber      CSV 行号（1 起，包含表头行）
 * @param externalBillId  外部账单标识
 * @param outcome         处理结果：IMPORTED、DUPLICATE、REJECTED
 * @param matchState      匹配状态，未进入匹配流程时为 null
 * @param message         说明信息，拒绝时给出原因
 * @author 余炘洋
 * @date 2026-10-17
 */
public record VendorBillImportRow(int lineNumber,
                                  String externalBillId,
                                  String outcome,
                                  String matchState,
                                  String message) {
}
