package com.lingsql.tenant.service;

import com.lingsql.config.LingSqlProperties;
import com.lingsql.ops.OpsAlertService;
import com.lingsql.tenant.entity.TenantInfo;
import com.lingsql.tenant.model.TenantLifecycleSnapshot;
import com.lingsql.tenant.model.TenantLifecycleState;
import com.lingsql.tenant.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 租户到期提醒调度任务。
 *
 * @author 余炘洋
 * @date 2026-02-27
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TenantLifecycleReminderScheduler {

    private static final Integer NOT_DELETED = 0;
    private static final Integer ACTIVE_STATUS = 1;
    private static final Duration DEFAULT_REMINDER_DEDUPE_TTL = Duration.ofDays(2);
    private static final String REDIS_REMINDER_DEDUPE_PREFIX = "lingsql:tenant:lifecycle:reminder:";

    private final TenantRepository tenantRepository;
    private final TenantLifecycleService lifecycleService;
    private final OpsAlertService opsAlertService;
    private final LingSqlProperties properties;
    private final StringRedisTemplate stringRedisTemplate;
    private final Map<String, LocalDate> reminderRecord = new ConcurrentHashMap<>();

    /**
     * 发送即将到期租户提醒（T-7/T-3/T-1）。
     * 使用内存去重，确保同租户同提醒天数每天仅发送一次。
     *
     * @author 余炘洋
     * @date 2026-02-27
     */
    @Scheduled(cron = "${lingsql.tenant-lifecycle.reminder-cron:0 0 9 * * ?}")
    public void remindExpiringTenants() {
        if (!lifecycleService.isLifecycleEnabled()) {
            return;
        }
        LingSqlProperties.TenantLifecycle lifecycle = properties.getTenantLifecycle();
        if (lifecycle == null || !lifecycle.isReminderEnabled()) {
            return;
        }
        Set<Long> reminderDays = resolveReminderDays(lifecycle.getReminderDays());
        if (reminderDays.isEmpty()) {
            return;
        }
        Duration dedupeTtl = resolveReminderDedupeTtl(lifecycle.getReminderDedupeTtl());
        List<TenantInfo> tenants = tenantRepository.findByIsDeletedAndStatusAndExpireTimeIsNotNull(NOT_DELETED, ACTIVE_STATUS);
        if (tenants == null || tenants.isEmpty()) {
            return;
        }
        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();
        long memoryReserveDays = Math.max(2L, dedupeTtl.toDays());
        cleanupReminderHistory(today.minusDays(memoryReserveDays));

        int remindedCount = 0;
        for (TenantInfo tenant : tenants) {
            TenantLifecycleSnapshot snapshot = lifecycleService.evaluate(tenant, now);
            if (snapshot.state() != TenantLifecycleState.EXPIRING_SOON) {
                continue;
            }
            long daysToExpire = snapshot.daysToExpire();
            if (!reminderDays.contains(daysToExpire)) {
                continue;
            }
            String dedupeKey = tenant.getTenantId() + ":" + daysToExpire;
            ReminderAcquireResult acquireResult = acquireReminderTicket(dedupeKey, today, dedupeTtl);
            if (!acquireResult.acquired()) {
                continue;
            }
            Map<String, Object> details = new HashMap<>();
            details.put("tenantId", tenant.getTenantId());
            details.put("tenantName", tenant.getTenantName());
            details.put("daysToExpire", daysToExpire);
            details.put("expireTime", tenant.getExpireTime());
            details.put("planName", tenant.getPlanName());
            details.put("contactEmail", tenant.getContactEmail());

            boolean success = opsAlertService.sendAlert(
                    "租户即将到期提醒",
                    "租户将在 " + daysToExpire + " 天后到期，请及时跟进续期",
                    details
            );
            if (success) {
                remindedCount++;
            } else {
                rollbackReminderTicket(dedupeKey, today, acquireResult.redisBacked());
            }
        }
        if (remindedCount > 0) {
            log.info("租户到期提醒完成: remindedCount={}", remindedCount);
        }
    }

    private ReminderAcquireResult acquireReminderTicket(String dedupeKey, LocalDate today, Duration dedupeTtl) {
        Boolean redisAcquire = tryAcquireReminderTicketRedis(dedupeKey, today, dedupeTtl);
        if (redisAcquire != null) {
            return new ReminderAcquireResult(redisAcquire, true);
        }
        LocalDate lastDay = reminderRecord.get(dedupeKey);
        if (today.equals(lastDay)) {
            return new ReminderAcquireResult(false, false);
        }
        reminderRecord.put(dedupeKey, today);
        return new ReminderAcquireResult(true, false);
    }

    private Boolean tryAcquireReminderTicketRedis(String dedupeKey, LocalDate today, Duration dedupeTtl) {
        if (stringRedisTemplate == null) {
            return null;
        }
        String redisKey = buildRedisDedupeKey(dedupeKey, today);
        try {
            return stringRedisTemplate.opsForValue().setIfAbsent(redisKey, "1", dedupeTtl);
        } catch (Exception ex) {
            log.warn("租户提醒 Redis 去重失败，回退内存去重: key={}, error={}", redisKey, ex.getMessage());
            return null;
        }
    }

    private void rollbackReminderTicket(String dedupeKey, LocalDate today, boolean redisBacked) {
        if (redisBacked) {
            if (stringRedisTemplate == null) {
                return;
            }
            String redisKey = buildRedisDedupeKey(dedupeKey, today);
            try {
                stringRedisTemplate.delete(redisKey);
            } catch (Exception ex) {
                log.warn("租户提醒 Redis 回滚去重失败: key={}, error={}", redisKey, ex.getMessage());
            }
            return;
        }
        LocalDate current = reminderRecord.get(dedupeKey);
        if (today.equals(current)) {
            reminderRecord.remove(dedupeKey);
        }
    }

    private Set<Long> resolveReminderDays(List<Integer> days) {
        if (days == null || days.isEmpty()) {
            return Set.of(7L, 3L, 1L);
        }
        return days.stream()
                .filter(item -> item != null && item > 0)
                .map(Integer::longValue)
                .collect(Collectors.toSet());
    }

    private void cleanupReminderHistory(LocalDate threshold) {
        reminderRecord.entrySet().removeIf(entry -> entry.getValue() == null || entry.getValue().isBefore(threshold));
    }

    private Duration resolveReminderDedupeTtl(Duration configured) {
        if (configured == null || configured.isNegative() || configured.isZero()) {
            return DEFAULT_REMINDER_DEDUPE_TTL;
        }
        return configured;
    }

    private String buildRedisDedupeKey(String dedupeKey, LocalDate today) {
        return REDIS_REMINDER_DEDUPE_PREFIX + dedupeKey + ":" + today;
    }

    private record ReminderAcquireResult(boolean acquired, boolean redisBacked) {
    }
}
