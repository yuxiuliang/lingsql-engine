package com.lingsql.billing.service;

import com.lingsql.billing.entity.VendorBillEvent;
import com.lingsql.billing.repository.VendorBillEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 供应商账单导入服务：解析受控 CSV，落库为不可变账单事件，用于后续对账。
 *
 * @author 余炘洋
 * @date 2026-10-05
 */
@Service
@RequiredArgsConstructor
public class VendorBillImportService {

    private final VendorBillEventRepository repository;

    /**
     * 导入供应商账单 CSV。
     *
     * <p>每行格式：tenantId,providerName,externalBillId,amountMinor,currency,status</p>
     */
    public List<VendorBillEvent> importCsv(String csv) {
        List<VendorBillEvent> events = new ArrayList<>();
        if (csv == null || csv.isBlank()) {
            return events;
        }
        String[] lines = csv.split("\\R");
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) {
                continue;
            }
            String[] parts = line.split(",");
            if (parts.length < 6) {
                continue;
            }
            VendorBillEvent event = new VendorBillEvent();
            event.setTenantId(parts[0].trim());
            event.setProviderName(parts[1].trim());
            event.setExternalBillId(parts[2].trim());
            event.setAmountMinor(Long.parseLong(parts[3].trim()));
            event.setCurrency(parts[4].trim());
            event.setStatus(parts[5].trim());
            event.setCreatedAt(LocalDateTime.now());
            events.add(repository.save(event));
        }
        return events;
    }
}
