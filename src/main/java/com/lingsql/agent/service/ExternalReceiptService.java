package com.lingsql.agent.service;

import com.lingsql.agent.entity.ExternalReceipt;
import com.lingsql.agent.repository.ExternalReceiptRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.time.LocalDateTime;

/**
 * 外部系统回执服务：对外操作仅记录已脱敏的回执摘要，失败状态保留为 UNKNOWN。
 *
 * @author 余炘洋
 * @date 2026-10-05
 */
@Service
@RequiredArgsConstructor
public class ExternalReceiptService {

    private final ExternalReceiptRepository repository;

    public ExternalReceipt record(String tenantId, String targetSystem, String receiptPayload, String status) throws Exception {
        ExternalReceipt receipt = new ExternalReceipt();
        receipt.setTenantId(tenantId);
        receipt.setTargetSystem(targetSystem);
        receipt.setReceiptDigest(sha256(receiptPayload));
        receipt.setStatus(status);
        receipt.setCreatedAt(LocalDateTime.now());
        return repository.save(receipt);
    }

    private String sha256(String value) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(value == null ? new byte[0] : value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        StringBuilder builder = new StringBuilder();
        for (byte b : hash) {
            builder.append(String.format("%02x", b));
        }
        return builder.toString();
    }
}
