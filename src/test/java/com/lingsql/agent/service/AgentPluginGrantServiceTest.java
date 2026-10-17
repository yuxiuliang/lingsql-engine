package com.lingsql.agent.service;

import com.lingsql.agent.entity.AgentPluginGrant;
import com.lingsql.agent.plugin.AgentPluginAccess;
import com.lingsql.agent.plugin.AgentPluginDescriptor;
import com.lingsql.agent.plugin.AgentPluginCost;
import com.lingsql.agent.plugin.AgentPluginRegistry;
import com.lingsql.agent.repository.AgentPluginGrantRepository;
import com.lingsql.common.exception.BizException;
import com.lingsql.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 租户插件授权服务测试：仅允许对受控业务插件授权，其余一律拒绝。
 *
 * @author 余炘洋
 * @date 2026-10-17
 */
class AgentPluginGrantServiceTest {

    private static final AgentPluginDescriptor ORCHESTRATED_DESCRIPTOR = new AgentPluginDescriptor(
            "chart_mapper", "1.0.0", AgentPluginAccess.ORCHESTRATED, AgentPluginCost.LOW, true, "图表映射");
    private static final AgentPluginDescriptor SYSTEM_DESCRIPTOR = new AgentPluginDescriptor(
            "secure_query", "1.0.0", AgentPluginAccess.SYSTEM_ONLY, AgentPluginCost.HIGH, false, "安全查询");

    private final AgentPluginGrantRepository repository = mock(AgentPluginGrantRepository.class);
    private final AgentPluginRegistry registry = mock(AgentPluginRegistry.class);
    private final AgentPluginGrantService service = new AgentPluginGrantService(repository, registry);

    @Test
    void isGrantedShouldFailClosedWhenRecordMissing() {
        when(repository.findByTenantIdAndPluginId("tenant-a", "chart_mapper")).thenReturn(Optional.empty());

        assertFalse(service.isGranted("tenant-a", "chart_mapper"));
    }

    @Test
    void isGrantedShouldReturnFalseForBlankInputWithoutQuery() {
        assertFalse(service.isGranted("  ", "chart_mapper"));
        assertFalse(service.isGranted("tenant-a", null));
        verifyNoInteractions(repository);
    }

    @Test
    void grantShouldRejectUnknownPlugin() {
        when(registry.findDescriptor("unknown_tool")).thenReturn(Optional.empty());

        BizException exception = assertThrows(BizException.class,
                () -> service.grant("tenant-a", "unknown_tool", true));

        assertEquals(ErrorCode.NOT_FOUND, exception.getErrorCode());
    }

    @Test
    void grantShouldRejectSystemOnlyPlugin() {
        when(registry.findDescriptor("secure_query")).thenReturn(Optional.of(SYSTEM_DESCRIPTOR));

        BizException exception = assertThrows(BizException.class,
                () -> service.grant("tenant-a", "secure_query", true));

        assertEquals(ErrorCode.INVALID_PARAM, exception.getErrorCode());
        verify(repository, org.mockito.Mockito.never()).save(any(AgentPluginGrant.class));
    }

    @Test
    void grantShouldRejectBlankTenant() {
        BizException exception = assertThrows(BizException.class,
                () -> service.grant(" ", "chart_mapper", true));

        assertEquals(ErrorCode.INVALID_PARAM, exception.getErrorCode());
        verifyNoInteractions(registry);
    }

    @Test
    void grantShouldUpdateExistingRecordAndKeepCreatedAt() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 10, 1, 10, 0);
        AgentPluginGrant existing = new AgentPluginGrant();
        existing.setId(9L);
        existing.setTenantId("tenant-a");
        existing.setPluginId("chart_mapper");
        existing.setGranted(false);
        existing.setCreatedAt(createdAt);
        when(registry.findDescriptor("chart_mapper")).thenReturn(Optional.of(ORCHESTRATED_DESCRIPTOR));
        when(repository.findByTenantIdAndPluginId("tenant-a", "chart_mapper")).thenReturn(Optional.of(existing));
        when(repository.save(any(AgentPluginGrant.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AgentPluginGrant result = service.grant("tenant-a", " chart_mapper ", true);

        assertEquals(9L, result.getId());
        assertTrue(result.getGranted());
        assertEquals(createdAt, result.getCreatedAt());
        verify(repository).save(existing);
    }

    @Test
    void grantShouldCreateNewRecordWithTimestamp() {
        when(registry.findDescriptor("chart_mapper")).thenReturn(Optional.of(ORCHESTRATED_DESCRIPTOR));
        when(repository.findByTenantIdAndPluginId("tenant-a", "chart_mapper")).thenReturn(Optional.empty());
        when(repository.save(any(AgentPluginGrant.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AgentPluginGrant result = service.grant("tenant-a", "chart_mapper", false);

        assertEquals("tenant-a", result.getTenantId());
        assertEquals("chart_mapper", result.getPluginId());
        assertFalse(result.getGranted());
        assertNotNull(result.getCreatedAt());
    }

    @Test
    void listByTenantShouldRejectBlankTenant() {
        BizException exception = assertThrows(BizException.class, () -> service.listByTenant(null));

        assertEquals(ErrorCode.INVALID_PARAM, exception.getErrorCode());
        verifyNoInteractions(repository);
    }

    @Test
    void listByTenantShouldTrimTenantId() {
        AgentPluginGrant grant = new AgentPluginGrant();
        grant.setTenantId("tenant-a");
        grant.setPluginId("chart_mapper");
        grant.setGranted(true);
        when(repository.findByTenantIdOrderByPluginIdAsc("tenant-a")).thenReturn(List.of(grant));

        List<AgentPluginGrant> grants = service.listByTenant(" tenant-a ");

        assertEquals(1, grants.size());
        assertEquals("chart_mapper", grants.getFirst().getPluginId());
    }
}
