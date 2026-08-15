package com.lingsql.billing;

import com.lingsql.common.exception.BizException;
import com.lingsql.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

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
    private static final String DECR_SCRIPT_TEXT = """
            local current = redis.call('GET', KEYS[1])
            if not current then
                return {0, 0, 0}
            end
            local before = tonumber(current)
            local cost = tonumber(ARGV[1])
            local after = before - cost
            if after < 0 then
                after = 0
            end
            redis.call('SET', KEYS[1], after)
            return {1, before, after}
            """;

    private static final DefaultRedisScript<List> DECR_SCRIPT = new DefaultRedisScript<>(DECR_SCRIPT_TEXT, List.class);
    private static final String RESERVE_SCRIPT_TEXT = """
            local current = redis.call('GET', KEYS[1])
            if not current then
                return {0, 0, 0}
            end
            local before = tonumber(current)
            local cost = tonumber(ARGV[1])
            if before < cost then
                return {0, before, before}
            end
            local after = before - cost
            redis.call('SET', KEYS[1], after)
            return {1, before, after}
            """;
    private static final DefaultRedisScript<List> RESERVE_SCRIPT = new DefaultRedisScript<>(RESERVE_SCRIPT_TEXT, List.class);

    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 获取当前余额（优先 Redis）。
     *
     * @param tenantId  租户ID
     * @param fallback 数据库余额
     * @return 余额
     * @author 余炘洋
     * @date 2026-01-29
     */
    public long getAvailableBalance(String tenantId, Long fallback) {
        if (!StringUtils.hasText(tenantId)) {
            return 0L;
        }
        long initial = resolveBalance(fallback);
        try {
            String key = balanceKey(tenantId);
            String cached = stringRedisTemplate.opsForValue().get(key);
            if (cached != null) {
                return parseLong(cached, initial);
            }
            stringRedisTemplate.opsForValue().setIfAbsent(key, String.valueOf(initial));
            String afterInit = stringRedisTemplate.opsForValue().get(key);
            return afterInit == null ? initial : parseLong(afterInit, initial);
        } catch (Exception ex) {
            log.warn("读取余额缓存失败: tenantId={}, error={}", tenantId, ex.getMessage());
            return initial;
        }
    }

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
     * 扣减余额（优先 Redis）。
     *
     * @param tenantId  租户ID
     * @param cost      扣费金额
     * @param fallback 数据库余额
     * @return 扣费结果
     * @author 余炘洋
     * @date 2026-01-29
     */
    public BillingChargeResult decreaseBalance(String tenantId, long cost, Long fallback) {
        if (!StringUtils.hasText(tenantId) || cost <= 0) {
            return BillingChargeResult.skip();
        }
        long initial = resolveBalance(fallback);
        try {
            String key = balanceKey(tenantId);
            stringRedisTemplate.opsForValue().setIfAbsent(key, String.valueOf(initial));
            List<?> result = stringRedisTemplate.execute(DECR_SCRIPT, List.of(key), String.valueOf(cost));
            BalanceSnapshot snapshot = parseSnapshot(result, cost, initial);
            if (!snapshot.available) {
                return BillingChargeResult.success(cost, initial, Math.max(0, initial - cost));
            }
            return BillingChargeResult.success(cost, snapshot.before, snapshot.after);
        } catch (Exception ex) {
            log.warn("扣减余额缓存失败: tenantId={}, error={}", tenantId, ex.getMessage());
            long before = initial;
            long after = Math.max(0, before - cost);
            return BillingChargeResult.success(cost, before, after);
        }
    }

    /**
     * 预扣余额（严格校验）。
     *
     * @param tenantId  租户ID
     * @param cost      预扣金额
     * @param fallback 数据库余额
     * @return 预扣结果
     * @author 余炘洋
     * @date 2026-01-29
     */
    public BillingChargeResult reserveBalance(String tenantId, long cost, Long fallback) {
        if (!StringUtils.hasText(tenantId) || cost <= 0) {
            return BillingChargeResult.skip();
        }
        long initial = resolveBalance(fallback);
        try {
            String key = balanceKey(tenantId);
            stringRedisTemplate.opsForValue().setIfAbsent(key, String.valueOf(initial));
            List<?> result = stringRedisTemplate.execute(RESERVE_SCRIPT, List.of(key), String.valueOf(cost));
            BalanceSnapshot snapshot = parseSnapshot(result, cost, initial);
            if (!snapshot.available) {
                return BillingChargeResult.skip();
            }
            return BillingChargeResult.success(cost, snapshot.before, snapshot.after);
        } catch (Exception ex) {
            log.warn("预扣余额缓存失败: tenantId={}, error={}", tenantId, ex.getMessage());
            return BillingChargeResult.skip();
        }
    }

    /**
     * 退回预扣余额。
     *
     * @param tenantId 租户ID
     * @param amount   退回金额
     * @author 余炘洋
     * @date 2026-01-29
     */
    public void refundBalance(String tenantId, long amount) {
        if (!StringUtils.hasText(tenantId) || amount <= 0) {
            return;
        }
        try {
            String key = balanceKey(tenantId);
            stringRedisTemplate.opsForValue().increment(key, amount);
        } catch (Exception ex) {
            log.warn("退回余额缓存失败: tenantId={}, error={}", tenantId, ex.getMessage());
        }
    }

    /**
     * 退回预扣余额，失败时中断当前结算并交由上层记录补偿故障。
     *
     * @param tenantId 租户ID
     * @param amount 退回金额
     * @author 余炘洋
     * @date 2026-08-15
     */
    public void refundBalanceRequired(String tenantId, long amount) {
        if (!StringUtils.hasText(tenantId) || amount <= 0) {
            return;
        }
        try {
            String key = balanceKey(tenantId);
            stringRedisTemplate.opsForValue().increment(key, amount);
        } catch (Exception ex) {
            throw new BizException(ErrorCode.BILLING_INVALID, "退回预扣余额失败，请人工核对账本", ex);
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

    private long parseLong(String value, long fallback) {
        if (!StringUtils.hasText(value)) {
            return fallback;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private BalanceSnapshot parseSnapshot(List<?> result, long cost, long fallback) {
        if (result == null || result.size() < 3) {
            long after = Math.max(0, fallback - cost);
            return new BalanceSnapshot(false, fallback, after);
        }
        long available = parseObject(result.get(0), 0);
        long before = parseObject(result.get(1), fallback);
        long after = parseObject(result.get(2), Math.max(0, before - cost));
        return new BalanceSnapshot(available == 1, before, after);
    }

    private long parseObject(Object value, long fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private record BalanceSnapshot(boolean available, long before, long after) {
    }
}
