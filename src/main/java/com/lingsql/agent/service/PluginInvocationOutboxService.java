package com.lingsql.agent.service;

import com.lingsql.agent.entity.PluginInvocationOutbox;
import com.lingsql.agent.repository.PluginInvocationOutboxRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 插件调用 Outbox 服务：记录每次调用的上下文与状态。
 *
 * @author 余炘洋
 * @date 2026-10-05
 */
@Service
@RequiredArgsConstructor
public class PluginInvocationOutboxService {

    private final PluginInvocationOutboxRepository repository;

    /**
     * 记录一次插件调用。
     *
     * @param tenantId   租户标识
     * @param executionId 执行标识
     * @param pluginId   插件标识
     * @param status     状态
     * @param costUnits  成本单位
     */
    public void record(String tenantId, String executionId, String pluginId, String status, Integer costUnits) {
        PluginInvocationOutbox outbox = new PluginInvocationOutbox();
        outbox.setTenantId(tenantId);
        outbox.setExecutionId(executionId);
        outbox.setPluginId(pluginId);
        outbox.setStatus(status);
        outbox.setCostUnits(costUnits);
        outbox.setCreatedAt(LocalDateTime.now());
        repository.save(outbox);
    }
}
