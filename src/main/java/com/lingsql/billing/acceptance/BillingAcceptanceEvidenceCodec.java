package com.lingsql.billing.acceptance;

import com.lingsql.common.exception.BizException;
import com.lingsql.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;

/**
 * 计费验收证据的规范化序列化与摘要。
 *
 * @author 余炘洋
 * @date 2026-08-15
 */
@Component
@RequiredArgsConstructor
class BillingAcceptanceEvidenceCodec {

    private final ObjectMapper objectMapper;

    String writeRequestIds(List<String> requestIds) {
        try {
            return objectMapper.writeValueAsString(requestIds);
        } catch (RuntimeException ex) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "计费验收请求集合序列化失败", ex);
        }
    }

    List<String> readRequestIds(String json) {
        try {
            return List.copyOf(Arrays.asList(objectMapper.readValue(json, String[].class)));
        } catch (RuntimeException ex) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "计费验收请求集合读取失败", ex);
        }
    }

    String requestIdsHash(List<String> requestIds) {
        return sha256(writeRequestIds(requestIds));
    }

    String receiptHash(String receiptId,
                       String sessionId,
                       String tenantId,
                       String requestIdsHash,
                       long startWatermark,
                       long endWatermark,
                       long balanceBefore,
                       long balanceAfter,
                       long scopeNetAmount,
                       long intervalNetAmount,
                       long unrelatedEventCount,
                       long invocationCount,
                       long settledCount,
                       long cancelledCount,
                       String closedBy,
                       LocalDateTime closedAt) {
        String canonical = String.join("\n",
                receiptId,
                sessionId,
                tenantId,
                requestIdsHash,
                Long.toString(startWatermark),
                Long.toString(endWatermark),
                Long.toString(balanceBefore),
                Long.toString(balanceAfter),
                Long.toString(scopeNetAmount),
                Long.toString(intervalNetAmount),
                Long.toString(unrelatedEventCount),
                Long.toString(invocationCount),
                Long.toString(settledCount),
                Long.toString(cancelledCount),
                closedBy,
                closedAt.toString()
        );
        return sha256(canonical);
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("JDK 缺少 SHA-256 算法", ex);
        }
    }
}
