package com.lingsql.billing;

import com.lingsql.billing.entity.VendorBillEvent;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

/**
 * 供应商账单证据摘要工具。
 *
 * <p>对导入账单的关键字段做大小写归一后计算 SHA-256，作为外部证据摘要固定到具体账单记录，
 * 供 UNKNOWN 物理调用裁决时引用；摘要只由不可变账单字段推导，重复计算结果稳定。</p>
 *
 * @author 余炘洋
 * @date 2026-10-17
 */
public final class VendorBillEvidenceDigest {

    private static final String SEPARATOR = "|";

    private VendorBillEvidenceDigest() {
    }

    /**
     * 计算账单证据摘要。
     *
     * @param event 供应商账单事件
     * @return SHA-256 十六进制摘要，事件为空时返回 null
     * @author 余炘洋
     * @date 2026-10-17
     */
    public static String digest(VendorBillEvent event) {
        if (event == null) {
            return null;
        }
        String canonical = String.join(SEPARATOR,
                normalizeUpper(event.getProviderName()),
                normalizeUpper(event.getExternalBillId()),
                event.getAmountMinor() == null ? "" : String.valueOf(event.getAmountMinor()),
                normalizeUpper(event.getCurrency()),
                normalizeUpper(event.getStatus()),
                normalizeUpper(event.getProviderRequestId()),
                event.getVendorTokens() == null ? "" : String.valueOf(event.getVendorTokens()));
        return sha256Hex(canonical);
    }

    /**
     * 计算文本 SHA-256 摘要。
     *
     * @param text 文本
     * @return SHA-256 十六进制摘要
     * @author 余炘洋
     * @date 2026-10-17
     */
    public static String sha256Hex(String text) {
        return sha256Hex((text == null ? "" : text).getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 计算字节数组 SHA-256 摘要。
     *
     * @param bytes 字节数组
     * @return SHA-256 十六进制摘要
     * @author 余炘洋
     * @date 2026-10-17
     */
    public static String sha256Hex(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(bytes);
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte value : hash) {
                builder.append(Character.forDigit((value >> 4) & 0xF, 16));
                builder.append(Character.forDigit(value & 0xF, 16));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前运行环境不支持 SHA-256", exception);
        }
    }

    private static String normalizeUpper(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
