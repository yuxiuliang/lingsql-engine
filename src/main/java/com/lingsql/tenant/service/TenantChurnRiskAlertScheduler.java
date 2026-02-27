package com.lingsql.tenant.service;

import com.lingsql.config.LingSqlProperties;
import com.lingsql.ops.OpsAlertService;
import com.lingsql.tenant.entity.TenantInfo;
import com.lingsql.tenant.model.TenantLifecycleSnapshot;
import com.lingsql.tenant.model.TenantRiskLevel;
import com.lingsql.tenant.model.TenantRiskSnapshot;
import com.lingsql.tenant.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 租户流失风险告警调度任务。
 *
 * @author 余炘洋
 * @date 2026-02-27
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TenantChurnRiskAlertScheduler {

    private static final Integer NOT_DELETED = 0;
    private static final int DEFAULT_THRESHOLD = 70;
    private static final int DEFAULT_TOP_N = 20;

    private final TenantRepository tenantRepository;
    private final TenantLifecycleService lifecycleService;
    private final OpsAlertService opsAlertService;
    private final LingSqlProperties properties;

    /**
     * 发送租户流失风险日报告警。
     *
     * @author 余炘洋
     * @date 2026-02-27
     */
    @Scheduled(cron = "${lingsql.tenant-lifecycle.risk-alert-cron:0 30 9 * * ?}")
    public void alertHighRiskTenants() {
        if (!lifecycleService.isLifecycleEnabled()) {
            return;
        }
        LingSqlProperties.TenantLifecycle lifecycle = properties.getTenantLifecycle();
        if (lifecycle == null || !lifecycle.isRiskAlertEnabled()) {
            return;
        }
        int threshold = resolveThreshold(lifecycle.getRiskAlertThreshold());
        int topN = resolveTopN(lifecycle.getRiskAlertTopN());
        List<TenantInfo> tenants = tenantRepository.findByIsDeleted(NOT_DELETED);
        if (tenants == null || tenants.isEmpty()) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        List<RiskTenantItem> riskTenants = new ArrayList<>();
        for (TenantInfo tenant : tenants) {
            TenantRiskSnapshot riskSnapshot = lifecycleService.evaluateRisk(tenant, now);
            if (riskSnapshot == null || riskSnapshot.score() < threshold) {
                continue;
            }
            TenantLifecycleSnapshot lifecycleSnapshot = lifecycleService.evaluate(tenant, now);
            riskTenants.add(new RiskTenantItem(
                    tenant.getTenantId(),
                    tenant.getTenantName(),
                    tenant.getContactEmail(),
                    riskSnapshot.score(),
                    riskSnapshot.level(),
                    lifecycleSnapshot.daysToExpire(),
                    lifecycleSnapshot.state().name()
            ));
        }
        if (riskTenants.isEmpty()) {
            return;
        }
        riskTenants.sort(Comparator.comparingInt(RiskTenantItem::score).reversed());
        long criticalCount = riskTenants.stream().filter(item -> item.level() == TenantRiskLevel.CRITICAL).count();
        long highCount = riskTenants.stream().filter(item -> item.level() == TenantRiskLevel.HIGH).count();
        long mediumCount = riskTenants.stream().filter(item -> item.level() == TenantRiskLevel.MEDIUM).count();
        List<Map<String, Object>> topTenants = new ArrayList<>();
        int limit = Math.min(topN, riskTenants.size());
        for (int i = 0; i < limit; i++) {
            topTenants.add(riskTenants.get(i).toDetailMap());
        }

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("threshold", threshold);
        details.put("totalAtRisk", riskTenants.size());
        details.put("criticalCount", criticalCount);
        details.put("highCount", highCount);
        details.put("mediumCount", mediumCount);
        details.put("topTenants", topTenants);
        boolean success = opsAlertService.sendAlert(
                "租户流失风险日报",
                "检测到 " + riskTenants.size() + " 个中高风险租户，请及时安排续期与客户回访",
                details
        );
        if (success) {
            log.info("租户流失风险告警发送完成: totalAtRisk={}, threshold={}", riskTenants.size(), threshold);
        }
    }

    private int resolveThreshold(int value) {
        if (value < 0 || value > 100) {
            return DEFAULT_THRESHOLD;
        }
        return value;
    }

    private int resolveTopN(int value) {
        if (value <= 0) {
            return DEFAULT_TOP_N;
        }
        return value;
    }

    private record RiskTenantItem(String tenantId,
                                  String tenantName,
                                  String contactEmail,
                                  int score,
                                  TenantRiskLevel level,
                                  long daysToExpire,
                                  String lifecycleState) {

        private Map<String, Object> toDetailMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("tenantId", tenantId);
            map.put("tenantName", tenantName);
            map.put("riskScore", score);
            map.put("riskLevel", level.name());
            map.put("lifecycleState", lifecycleState);
            map.put("contactEmail", contactEmail);
            map.put("daysToExpire", daysToExpire == Long.MAX_VALUE ? null : daysToExpire);
            return map;
        }
    }
}

