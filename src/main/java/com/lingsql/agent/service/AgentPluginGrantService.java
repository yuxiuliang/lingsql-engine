package com.lingsql.agent.service;

import com.lingsql.agent.entity.AgentPluginGrant;
import com.lingsql.agent.plugin.AgentPluginAccess;
import com.lingsql.agent.plugin.AgentPluginDescriptor;
import com.lingsql.agent.plugin.AgentPluginRegistry;
import com.lingsql.agent.repository.AgentPluginGrantRepository;
import com.lingsql.common.exception.BizException;
import com.lingsql.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 租户插件授权服务：维护租户可用的受控业务插件白名单。
 *
 * <p>受控业务插件（{@link AgentPluginAccess#ORCHESTRATED}）必须由租户显式授权后才能执行，
 * 未授权时在插件执行网关层直接拒绝，避免默认向业务暴露全部插件；
 * 系统插件由受控工作流固定调用，不参与租户授权，禁止通过授权关闭安全链路。</p>
 *
 * @author 余炘洋
 * @date 2026-10-05
 */
@Service
@RequiredArgsConstructor
public class AgentPluginGrantService {

    private final AgentPluginGrantRepository repository;
    private final AgentPluginRegistry pluginRegistry;

    /**
     * 判断租户是否授权某插件。
     *
     * @param tenantId 租户标识
     * @param pluginId 插件标识
     * @return 是否已授权
     */
    public boolean isGranted(String tenantId, String pluginId) {
        if (!StringUtils.hasText(tenantId) || !StringUtils.hasText(pluginId)) {
            return false;
        }
        return repository.findByTenantIdAndPluginId(tenantId.trim(), pluginId.trim())
                .map(AgentPluginGrant::getGranted)
                .map(Boolean.TRUE::equals)
                .orElse(false);
    }

    /**
     * 查询租户授权记录：只返回显式写入过的插件，未写入的插件按未授权处理。
     *
     * @param tenantId 租户标识
     * @return 授权记录列表
     * @author 余炘洋
     * @date 2026-10-17
     */
    public List<AgentPluginGrant> listByTenant(String tenantId) {
        requireTenantId(tenantId);
        return repository.findByTenantIdOrderByPluginIdAsc(tenantId.trim());
    }

    /**
     * 设置租户插件授权：仅允许对已注册的受控业务插件授权。
     *
     * @param tenantId 租户标识
     * @param pluginId 插件标识
     * @param granted  是否授权
     * @return 最新授权记录
     * @author 余炘洋
     * @date 2026-10-17
     */
    public AgentPluginGrant grant(String tenantId, String pluginId, boolean granted) {
        requireTenantId(tenantId);
        if (!StringUtils.hasText(pluginId)) {
            throw new BizException(ErrorCode.INVALID_PARAM, "插件标识不能为空");
        }
        String normalizedTenantId = tenantId.trim();
        String normalizedPluginId = pluginId.trim();
        AgentPluginDescriptor descriptor = pluginRegistry.findDescriptor(normalizedPluginId)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "Agent 插件不存在: " + normalizedPluginId));
        if (descriptor.access() != AgentPluginAccess.ORCHESTRATED) {
            throw new BizException(ErrorCode.INVALID_PARAM,
                    "系统插件由受控工作流固定调用，不支持租户授权: " + normalizedPluginId);
        }
        AgentPluginGrant grant = repository.findByTenantIdAndPluginId(normalizedTenantId, normalizedPluginId)
                .orElseGet(AgentPluginGrant::new);
        grant.setTenantId(normalizedTenantId);
        grant.setPluginId(normalizedPluginId);
        grant.setGranted(granted);
        if (grant.getCreatedAt() == null) {
            grant.setCreatedAt(LocalDateTime.now());
        }
        return repository.save(grant);
    }

    private void requireTenantId(String tenantId) {
        if (!StringUtils.hasText(tenantId)) {
            throw new BizException(ErrorCode.INVALID_PARAM, "租户标识不能为空");
        }
    }
}
