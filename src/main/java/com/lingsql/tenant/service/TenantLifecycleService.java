package com.lingsql.tenant.service;

import com.lingsql.config.LingSqlProperties;
import com.lingsql.tenant.entity.TenantInfo;
import com.lingsql.tenant.model.TenantLifecycleSnapshot;
import com.lingsql.tenant.model.TenantLifecycleState;
import com.lingsql.tenant.model.TenantRiskLevel;
import com.lingsql.tenant.model.TenantRiskSnapshot;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * 租户生命周期治理服务。
 *
 * @author 余炘洋
 * @date 2026-02-27
 */
@Service
public class TenantLifecycleService {

    private static final String TENANT_TYPE_TRIAL = "TRIAL";
    private static final String SERVICE_LEVEL_FREE = "FREE";

    private final LingSqlProperties properties;

    public TenantLifecycleService(LingSqlProperties properties) {
        this.properties = properties;
    }

    /**
     * 评估租户当前生命周期状态。
     *
     * @param tenant 租户
     * @return 生命周期快照
     * @author 余炘洋
     * @date 2026-02-27
     */
    public TenantLifecycleSnapshot evaluate(TenantInfo tenant) {
        return evaluate(tenant, LocalDateTime.now());
    }

    /**
     * 按指定时间评估租户生命周期状态。
     *
     * @param tenant 租户
     * @param now    当前时间
     * @return 生命周期快照
     * @author 余炘洋
     * @date 2026-02-27
     */
    public TenantLifecycleSnapshot evaluate(TenantInfo tenant, LocalDateTime now) {
        if (tenant == null) {
            return new TenantLifecycleSnapshot(TenantLifecycleState.DISABLED, 0, 0);
        }
        LocalDateTime expireTime = tenant.getExpireTime();
        if (tenant.getStatus() == null || tenant.getStatus() != 1) {
            if (expireTime == null) {
                return new TenantLifecycleSnapshot(TenantLifecycleState.DISABLED, 0, 0);
            }
            long daysToExpire = ChronoUnit.DAYS.between(now, expireTime);
            Duration gracePeriod = resolveGracePeriod();
            LocalDateTime graceEnd = expireTime.plus(gracePeriod);
            if (now.isAfter(graceEnd)) {
                return new TenantLifecycleSnapshot(TenantLifecycleState.SUSPENDED, daysToExpire, 0);
            }
            return new TenantLifecycleSnapshot(TenantLifecycleState.DISABLED, daysToExpire, 0);
        }
        if (!isLifecycleEnabled()) {
            return new TenantLifecycleSnapshot(TenantLifecycleState.ACTIVE, 0, 0);
        }
        if (expireTime == null) {
            return new TenantLifecycleSnapshot(TenantLifecycleState.ACTIVE, Long.MAX_VALUE, 0);
        }

        long daysToExpire = ChronoUnit.DAYS.between(now, expireTime);
        if (!now.isAfter(expireTime)) {
            int expiringSoonDays = Math.max(resolveExpiringSoonDays(), 0);
            if (daysToExpire <= expiringSoonDays) {
                return new TenantLifecycleSnapshot(TenantLifecycleState.EXPIRING_SOON, daysToExpire, 0);
            }
            return new TenantLifecycleSnapshot(TenantLifecycleState.ACTIVE, daysToExpire, 0);
        }

        Duration gracePeriod = resolveGracePeriod();
        LocalDateTime graceEnd = expireTime.plus(gracePeriod);
        if (!now.isAfter(graceEnd)) {
            long graceRemaining = Math.max(0, ChronoUnit.DAYS.between(now, graceEnd));
            return new TenantLifecycleSnapshot(TenantLifecycleState.GRACE_PERIOD, daysToExpire, graceRemaining);
        }
        return new TenantLifecycleSnapshot(TenantLifecycleState.SUSPENDED, daysToExpire, 0);
    }

    /**
     * 试用租户创建时自动补齐到期时间。
     *
     * @param tenant 租户
     * @author 余炘洋
     * @date 2026-02-27
     */
    public void applyDefaultExpireTimeForTrial(TenantInfo tenant) {
        if (tenant == null || tenant.getExpireTime() != null) {
            return;
        }
        if (!StringUtils.hasText(tenant.getTenantType())) {
            return;
        }
        if (!TENANT_TYPE_TRIAL.equalsIgnoreCase(tenant.getTenantType().trim())) {
            return;
        }
        int trialDays = Math.max(resolveTrialDefaultDays(), 1);
        tenant.setExpireTime(LocalDateTime.now().plusDays(trialDays));
    }

    /**
     * 生成生命周期拒绝原因。
     *
     * @param snapshot 生命周期快照
     * @return 提示信息
     * @author 余炘洋
     * @date 2026-02-27
     */
    public String resolveDenyMessage(TenantLifecycleSnapshot snapshot) {
        if (snapshot == null) {
            return "租户状态异常，请联系管理员";
        }
        return switch (snapshot.state()) {
            case DISABLED -> "租户已停用，请联系商务或管理员";
            case SUSPENDED -> "租户已到期并超过宽限期，请联系管理员续期";
            default -> "租户不可用，请联系管理员";
        };
    }

    /**
     * 评估租户流失风险。
     *
     * @param tenant 租户
     * @return 风险快照
     * @author 余炘洋
     * @date 2026-02-27
     */
    public TenantRiskSnapshot evaluateRisk(TenantInfo tenant) {
        return evaluateRisk(tenant, LocalDateTime.now());
    }

    /**
     * 按指定时间评估租户流失风险。
     *
     * @param tenant 租户
     * @param now    当前时间
     * @return 风险快照
     * @author 余炘洋
     * @date 2026-02-27
     */
    public TenantRiskSnapshot evaluateRisk(TenantInfo tenant, LocalDateTime now) {
        if (tenant == null) {
            return new TenantRiskSnapshot(100, TenantRiskLevel.CRITICAL, List.of("租户记录不存在"));
        }
        TenantLifecycleSnapshot lifecycleSnapshot = evaluate(tenant, now);
        List<String> reasons = new ArrayList<>();
        int score = baseRiskScore(lifecycleSnapshot.state());

        if (tenant.getStatus() == null || tenant.getStatus() != 1) {
            score += 8;
            reasons.add("租户当前处于停用状态");
        }

        long daysToExpire = lifecycleSnapshot.daysToExpire();
        if (daysToExpire != Long.MAX_VALUE) {
            if (daysToExpire < 0) {
                score += 10;
                reasons.add("租户已到期未续期");
            } else if (daysToExpire <= 1) {
                score += 20;
                reasons.add("距离到期不足 1 天");
            } else if (daysToExpire <= 3) {
                score += 14;
                reasons.add("距离到期不足 3 天");
            } else if (daysToExpire <= 7) {
                score += 8;
                reasons.add("距离到期不足 7 天");
            }
        }

        Long quotaBalance = tenant.getQuotaBalance();
        if (quotaBalance != null) {
            if (quotaBalance <= 0) {
                score += 10;
                reasons.add("可用额度已耗尽");
            } else if (quotaBalance < 100) {
                score += 4;
                reasons.add("可用额度偏低");
            }
        }

        if (isTrialTenant(tenant)) {
            score += 8;
            reasons.add("试用租户存在较高流失波动");
        }
        if (isFreeServiceLevel(tenant)) {
            score += 5;
            reasons.add("免费套餐续费转化概率较低");
        }
        if (!StringUtils.hasText(tenant.getContactEmail()) && !StringUtils.hasText(tenant.getContactPhone())) {
            score += 5;
            reasons.add("缺少有效联系人信息");
        }
        if (lifecycleSnapshot.state() == TenantLifecycleState.SUSPENDED) {
            reasons.add("租户已超宽限期并被冻结");
        } else if (lifecycleSnapshot.state() == TenantLifecycleState.GRACE_PERIOD) {
            reasons.add("租户处于宽限期");
        } else if (lifecycleSnapshot.state() == TenantLifecycleState.EXPIRING_SOON) {
            reasons.add("租户进入即将到期阶段");
        }

        int normalizedScore = Math.max(0, Math.min(score, 100));
        TenantRiskLevel level = resolveRiskLevel(normalizedScore);
        if (reasons.isEmpty()) {
            reasons.add("经营状态稳定");
        }
        return new TenantRiskSnapshot(normalizedScore, level, List.copyOf(reasons));
    }

    /**
     * 是否启用生命周期治理。
     *
     * @return true-启用
     * @author 余炘洋
     * @date 2026-02-27
     */
    public boolean isLifecycleEnabled() {
        LingSqlProperties.TenantLifecycle lifecycle = properties.getTenantLifecycle();
        return lifecycle == null || lifecycle.isEnabled();
    }

    /**
     * 是否启用自动冻结。
     *
     * @return true-启用
     * @author 余炘洋
     * @date 2026-02-27
     */
    public boolean isAutoSuspendEnabled() {
        LingSqlProperties.TenantLifecycle lifecycle = properties.getTenantLifecycle();
        return lifecycle == null || lifecycle.isAutoSuspendEnabled();
    }

    private int baseRiskScore(TenantLifecycleState state) {
        if (state == null) {
            return 50;
        }
        return switch (state) {
            case ACTIVE -> 12;
            case EXPIRING_SOON -> 55;
            case GRACE_PERIOD -> 75;
            case SUSPENDED -> 90;
            case DISABLED -> 80;
        };
    }

    private TenantRiskLevel resolveRiskLevel(int score) {
        if (score >= 90) {
            return TenantRiskLevel.CRITICAL;
        }
        if (score >= 75) {
            return TenantRiskLevel.HIGH;
        }
        if (score >= 50) {
            return TenantRiskLevel.MEDIUM;
        }
        return TenantRiskLevel.LOW;
    }

    private boolean isTrialTenant(TenantInfo tenant) {
        if (tenant == null || !StringUtils.hasText(tenant.getTenantType())) {
            return false;
        }
        return TENANT_TYPE_TRIAL.equalsIgnoreCase(tenant.getTenantType().trim());
    }

    private boolean isFreeServiceLevel(TenantInfo tenant) {
        if (tenant == null || !StringUtils.hasText(tenant.getServiceLevel())) {
            return false;
        }
        return SERVICE_LEVEL_FREE.equalsIgnoreCase(tenant.getServiceLevel().trim());
    }

    private Duration resolveGracePeriod() {
        LingSqlProperties.TenantLifecycle lifecycle = properties.getTenantLifecycle();
        if (lifecycle == null || lifecycle.getGracePeriod() == null || lifecycle.getGracePeriod().isNegative()) {
            return Duration.ofDays(7);
        }
        return lifecycle.getGracePeriod();
    }

    private int resolveExpiringSoonDays() {
        LingSqlProperties.TenantLifecycle lifecycle = properties.getTenantLifecycle();
        if (lifecycle == null) {
            return 7;
        }
        return lifecycle.getExpiringSoonDays();
    }

    private int resolveTrialDefaultDays() {
        LingSqlProperties.TenantLifecycle lifecycle = properties.getTenantLifecycle();
        if (lifecycle == null) {
            return 14;
        }
        return lifecycle.getTrialDefaultDays();
    }
}
