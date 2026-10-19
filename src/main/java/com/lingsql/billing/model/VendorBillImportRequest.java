package com.lingsql.billing.model;

/**
 * 供应商账单导入请求。
 *
 * @param csv 受控 CSV 文本
 * @author 余炘洋
 * @date 2026-10-17
 */
public record VendorBillImportRequest(String csv) {
}
