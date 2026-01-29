package com.lingsql.billing;

import com.lingsql.billing.entity.BillingLedgerEvent;
import com.lingsql.billing.model.BillingChargeItem;
import com.lingsql.billing.repository.BillingLedgerEventRepository;
import com.lingsql.config.LingSqlProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 计费账本服务。
 *
 * @author 余炘洋
 * @date 2026-01-29
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BillingLedgerService {

    private static final String EVENT_GUARD_KEY_PREFIX = "lingsql:billing:ledger:event:";
    private static final int STATUS_PENDING = 0;
    private static final int EVENT_TYPE_RESERVE = 0;
    private static final int EVENT_TYPE_CLOSE = 2;
    private static final Duration DEFAULT_CHARGED_KEY_TTL = Duration.ofDays(3);

    private final BillingLedgerEventRepository repository;
    private final StringRedisTemplate stringRedisTemplate;
    private final LingSqlProperties properties;

    /**
     * 写入预扣账本事件（幂等）。
     *
     * @param requestId 请求ID
     * @param tenantId  租户ID
     * @param eventSeq  事件序号
     * @param items     计费明细
     * @return 是否写入成功
     * @author 余炘洋
     * @date 2026-01-29
     */
    public boolean recordReserveEvents(String requestId, String tenantId, int eventSeq, List<BillingChargeItem> items) {
        return recordEvents(requestId, tenantId, eventSeq, EVENT_TYPE_RESERVE, items);
    }

    /**
     * 写入 CLOSE 账本事件（幂等）。
     *
     * @param requestId 请求ID
     * @param tenantId  租户ID
     * @param eventSeq  事件序号
     * @param items     计费明细
     * @return 是否写入成功
     * @author 余炘洋
     * @date 2026-01-29
     */
    public boolean recordCloseEvents(String requestId, String tenantId, int eventSeq, List<BillingChargeItem> items) {
        return recordEvents(requestId, tenantId, eventSeq, EVENT_TYPE_CLOSE, items);
    }

    private boolean recordEvents(String requestId, String tenantId, int eventSeq, int eventType, List<BillingChargeItem> items) {
        if (!StringUtils.hasText(requestId) || !StringUtils.hasText(tenantId) || eventSeq <= 0
                || CollectionUtils.isEmpty(items)) {
            return false;
        }
        if (isEventRecorded(requestId, eventSeq)) {
            return true;
        }
        LocalDateTime now = LocalDateTime.now();
        List<BillingLedgerEvent> entities = new ArrayList<>();
        for (BillingChargeItem item : items) {
            BillingLedgerEvent event = new BillingLedgerEvent();
            event.setRequestId(requestId);
            event.setEventSeq(eventSeq);
            event.setTenantId(tenantId);
            event.setModelName(item.modelName());
            event.setTokenCount(item.tokenCount());
            event.setUnitCost(item.unitCost());
            event.setCostAmount(item.costAmount());
            event.setBillingVersion(item.policyVersion());
            event.setStatus(STATUS_PENDING);
            event.setEventType(eventType);
            event.setCreateTime(now);
            entities.add(event);
        }
        try {
            repository.saveAll(entities);
        } catch (DataIntegrityViolationException ex) {
            return true;
        } catch (Exception ex) {
            log.warn("写入账本失败: requestId={}, eventSeq={}, tenantId={}, error={}",
                    requestId, eventSeq, tenantId, ex.getMessage());
            return false;
        }
        markEventRecorded(requestId, eventSeq);
        return true;
    }

    private boolean isEventRecorded(String requestId, int eventSeq) {
        String key = eventGuardKey(requestId, eventSeq);
        try {
            Boolean exists = stringRedisTemplate.hasKey(key);
            if (Boolean.TRUE.equals(exists)) {
                return true;
            }
        } catch (Exception ex) {
            log.warn("读取账本幂等Key失败: requestId={}, eventSeq={}, error={}", requestId, eventSeq, ex.getMessage());
        }
        return repository.existsByRequestIdAndEventSeq(requestId, eventSeq);
    }

    private void markEventRecorded(String requestId, int eventSeq) {
        String key = eventGuardKey(requestId, eventSeq);
        Duration ttl = resolveTtl();
        try {
            if (ttl == null || ttl.isZero() || ttl.isNegative()) {
                stringRedisTemplate.opsForValue().set(key, "1");
            } else {
                stringRedisTemplate.opsForValue().set(key, "1", ttl);
            }
        } catch (Exception ex) {
            log.warn("写入账本幂等Key失败: requestId={}, eventSeq={}, error={}", requestId, eventSeq, ex.getMessage());
        }
    }

    private String eventGuardKey(String requestId, int eventSeq) {
        return EVENT_GUARD_KEY_PREFIX + requestId + ":" + eventSeq;
    }

    private Duration resolveTtl() {
        LingSqlProperties.Billing billing = properties.getBilling();
        if (billing == null) {
            return DEFAULT_CHARGED_KEY_TTL;
        }
        return billing.getChargedKeyTtl();
    }
}
