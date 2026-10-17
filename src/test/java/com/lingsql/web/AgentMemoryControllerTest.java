package com.lingsql.web;

import com.lingsql.agent.entity.AgentMemoryFact;
import com.lingsql.agent.memory.AgentMemoryService;
import com.lingsql.agent.memory.MemorySourceType;
import com.lingsql.agent.memory.MemoryTier;
import com.lingsql.agent.memory.model.AgentMemoryCorrectRequest;
import com.lingsql.agent.memory.model.AgentMemorySaveRequest;
import com.lingsql.agent.memory.model.AgentMemoryView;
import com.lingsql.common.result.ApiResponse;
import com.lingsql.context.SessionInfo;
import com.lingsql.context.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 用户长期记忆接口测试：身份必须来自会话，越权与未登录一律失败关闭。
 *
 * @author 余炘洋
 * @date 2026-10-18
 */
class AgentMemoryControllerTest {

    private AgentMemoryService agentMemoryService;

    private AgentMemoryController controller;

    @BeforeEach
    void setUp() {
        agentMemoryService = Mockito.mock(AgentMemoryService.class);
        controller = new AgentMemoryController(agentMemoryService);
    }

    @AfterEach
    void clearContext() {
        TenantContext.clear();
    }

    @Test
    void listShouldRejectAnonymous() {
        ApiResponse<List<AgentMemoryView>> response = controller.list();

        assertEquals(401, response.getCode());
    }

    @Test
    void listShouldUseSessionScope() {
        TenantContext.setSessionInfo(new SessionInfo("t1", 7L));
        when(agentMemoryService.listOwn("t1", 7L)).thenReturn(List.of(fact(20L, "PERMANENT", 7L, "销售额口径为不含税金额")));

        ApiResponse<List<AgentMemoryView>> response = controller.list();

        assertEquals(200, response.getCode());
        assertEquals(1, response.getData().size());
        assertEquals("永久记忆", response.getData().get(0).getTierLabel());
        assertEquals("用户确认", response.getData().get(0).getSourceLabel());
        verify(agentMemoryService).listOwn("t1", 7L);
    }

    @Test
    void createShouldWriteUserPermanentMemory() {
        TenantContext.setSessionInfo(new SessionInfo("t1", 7L));
        AgentMemorySaveRequest request = new AgentMemorySaveRequest();
        request.setFactText("销售额口径为不含税金额");
        request.setRetentionDays(30);
        when(agentMemoryService.remember("t1", 7L, MemoryTier.PERMANENT, MemorySourceType.USER_CONFIRMED,
                "销售额口径为不含税金额", 30, "7"))
                .thenReturn(fact(100L, "PERMANENT", 7L, "销售额口径为不含税金额"));

        ApiResponse<AgentMemoryView> response = controller.create(request);

        assertEquals(200, response.getCode());
        assertEquals("销售额口径为不含税金额", response.getData().getFactText());
        verify(agentMemoryService).remember("t1", 7L, MemoryTier.PERMANENT, MemorySourceType.USER_CONFIRMED,
                "销售额口径为不含税金额", 30, "7");
    }

    @Test
    void correctShouldDelegateToOwnMemory() {
        TenantContext.setSessionInfo(new SessionInfo("t1", 7L));
        AgentMemoryCorrectRequest request = new AgentMemoryCorrectRequest();
        request.setFactText("新口径");
        when(agentMemoryService.correctOwn("t1", 7L, 5L, "新口径", null))
                .thenReturn(fact(101L, "PERMANENT", 7L, "新口径"));

        ApiResponse<AgentMemoryView> response = controller.correct(5L, request);

        assertEquals(200, response.getCode());
        assertEquals("新口径", response.getData().getFactText());
        verify(agentMemoryService).correctOwn("t1", 7L, 5L, "新口径", null);
    }

    @Test
    void revokeShouldDelegateToOwnMemory() {
        TenantContext.setSessionInfo(new SessionInfo("t1", 7L));
        AgentMemoryFact revoked = fact(5L, "PERMANENT", 7L, "旧口径");
        revoked.setStatus("REVOKED");
        when(agentMemoryService.revokeOwn("t1", 7L, 5L)).thenReturn(revoked);

        ApiResponse<AgentMemoryView> response = controller.revoke(5L);

        assertEquals(200, response.getCode());
        assertEquals("已撤销", response.getData().getStatusLabel());
        verify(agentMemoryService).revokeOwn("t1", 7L, 5L);
    }

    private AgentMemoryFact fact(Long id, String tier, Long userId, String factText) {
        LocalDateTime now = LocalDateTime.now();
        AgentMemoryFact fact = new AgentMemoryFact();
        fact.setId(id);
        fact.setTenantId("t1");
        fact.setUserId(userId);
        fact.setTier(tier);
        fact.setFactText(factText);
        fact.setSourceType("USER_CONFIRMED");
        fact.setStatus("ACTIVE");
        fact.setConfidence(90);
        fact.setVersion(1);
        fact.setCreatedBy("7");
        fact.setCreatedAt(now);
        fact.setUpdatedAt(now);
        fact.setExpiresAt(now.plusDays(30));
        return fact;
    }
}
