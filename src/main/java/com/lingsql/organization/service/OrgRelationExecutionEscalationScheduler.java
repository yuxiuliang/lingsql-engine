package com.lingsql.organization.service;

import com.lingsql.config.LingSqlProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 组织关系执行项逾期升级调度任务。
 *
 * @author 余炘洋
 * @date 2026-04-12
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrgRelationExecutionEscalationScheduler {

    private final OrgRelationExecutionEscalationService escalationService;
    private final LingSqlProperties properties;

    /**
     * 扫描并升级逾期执行项。
     *
     * @author 余炘洋
     * @date 2026-04-12
     */
    @Scheduled(cron = "${lingsql.org-execution.overdue-escalation-cron:0 0/15 * * * ?}")
    public void refreshExecutionEscalations() {
        LingSqlProperties.OrgExecution config = properties.getOrgExecution();
        if (config == null || !config.isEnabled() || !config.isOverdueEscalationEnabled()) {
            return;
        }
        int changed = escalationService.refreshEscalations();
        if (changed > 0) {
            log.info("组织执行项升级扫描完成: changed={}", changed);
        }
    }
}
