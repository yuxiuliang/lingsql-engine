package com.lingsql.billing;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 计费余额缓存服务。
 *
 * @author 余炘洋
 * @date 2026-01-29
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BillingBalanceService {

    private static final String BALANCE_KEY_PREFIX = "lingsql:billing:balance:";
    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 重置余额缓存。
     *
     * @param tenantId 租户ID
     * @param balance  余额
     * @author 余炘洋
     * @date 2026-01-29
     */
    public void resetBalance(String tenantId, Long balance) {
        if (!StringUtils.hasText(tenantId)) {
            return;
        }
        long normalized = resolveBalance(balance);
        try {
            stringRedisTemplate.opsForValue().set(balanceKey(tenantId), String.valueOf(normalized));
        } catch (Exception ex) {
            log.warn("重置余额缓存失败: tenantId={}, error={}", tenantId, ex.getMessage());
        }
    }

    /**
     * 使余额投影失效。资金事实已由 MySQL 事务提交，Redis 仅作为可重建投影。
     *
     * @param tenantId 租户ID
     * @author 余炘洋
     * @date 2026-08-15
     */
    public void evictBalance(String tenantId) {
        if (!StringUtils.hasText(tenantId)) {
            return;
        }
        try {
            stringRedisTemplate.delete(balanceKey(tenantId));
        } catch (Exception ex) {
            log.warn("余额投影失效失败，将由数据库余额覆盖: tenantId={}, error={}", tenantId, ex.getMessage());
        }
    }

    private String balanceKey(String tenantId) {
        return BALANCE_KEY_PREFIX + tenantId;
    }

    private long resolveBalance(Long value) {
        if (value == null || value < 0) {
            return 0L;
        }
        return value;
    }

}
