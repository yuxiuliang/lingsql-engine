package com.lingsql.web;

import com.lingsql.agent.entity.AgentMemoryFact;
import com.lingsql.agent.memory.AgentMemoryService;
import com.lingsql.agent.memory.MemoryCandidateService;
import com.lingsql.agent.memory.MemorySourceType;
import com.lingsql.agent.memory.MemoryTier;
import com.lingsql.agent.memory.model.AgentMemoryCorrectRequest;
import com.lingsql.agent.memory.model.AgentMemoryExportView;
import com.lingsql.agent.memory.model.AgentMemoryExtractRequest;
import com.lingsql.agent.memory.model.AgentMemoryRejectRequest;
import com.lingsql.agent.memory.model.AgentMemorySaveRequest;
import com.lingsql.agent.memory.model.AgentMemoryView;
import com.lingsql.agent.orchestration.AgentExecutionContext;
import com.lingsql.common.result.ApiResponse;
import com.lingsql.context.SessionInfo;
import com.lingsql.context.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 用户长期记忆接口测试：身份必须来自会话，越权与未登录一律失败关闭；
 * 候选提取、确认与拒绝必须绑定会话用户。
 *
 * @author 余炘洋
 * @date 2026-10-18
 */
class AgentMemoryControllerTest {

    private AgentMemoryService agentMemoryService;

    private MemoryCandidateService memoryCandidateService;

    private AgentMemoryController controller;

    @BeforeEach
    void setUp() {
        agentMemoryService = Mockito.mock(AgentMemoryService.class);
        memoryCandidateService = Mockito.mock(MemoryCandidateService.class);
        controller = new AgentMemoryController(agentMemoryService, memoryCandidateService);
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

    @Test
    void listCandidatesShouldRejectAnonymous() {
        ApiResponse<List<AgentMemoryView>> response = controller.listCandidates();

        assertEquals(401, response.getCode());
    }

    @Test
    void listCandidatesShouldUseSessionScope() {
        TenantContext.setSessionInfo(new SessionInfo("t1", 7L));
        AgentMemoryFact candidate = fact(60L, "PERMANENT", 7L, "候选口径");
        candidate.setStatus("CANDIDATE");
        candidate.setSourceType("MODEL_CANDIDATE");
        candidate.setSensitivity("SENSITIVE");
        when(memoryCandidateService.listOwnCandidates("t1", 7L)).thenReturn(List.of(candidate));

        ApiResponse<List<AgentMemoryView>> response = controller.listCandidates();

        assertEquals(200, response.getCode());
        assertEquals(1, response.getData().size());
        assertEquals("候选待确认", response.getData().get(0).getStatusLabel());
        assertEquals("模型候选", response.getData().get(0).getSourceLabel());
        assertEquals("敏感", response.getData().get(0).getSensitivityLabel());
        verify(memoryCandidateService).listOwnCandidates("t1", 7L);
    }

    @Test
    void extractCandidatesShouldBindSessionUserAndConversation() {
        TenantContext.setSessionInfo(new SessionInfo("t1", 7L));
        TenantContext.setConversationId("conv-9");
        AgentMemoryExtractRequest request = new AgentMemoryExtractRequest();
        request.setSourceRef("manual-ref");
        request.setMaxCandidates(3);
        AgentMemoryFact candidate = fact(61L, "PERMANENT", 7L, "候选口径");
        candidate.setStatus("CANDIDATE");
        candidate.setSourceType("MODEL_CANDIDATE");
        when(memoryCandidateService.extractCandidates(any(AgentExecutionContext.class), eq("manual-ref"), eq(3)))
                .thenReturn(List.of(candidate));

        ApiResponse<List<AgentMemoryView>> response = controller.extractCandidates(request);

        assertEquals(200, response.getCode());
        assertEquals(1, response.getData().size());
        ArgumentCaptor<AgentExecutionContext> captor = ArgumentCaptor.forClass(AgentExecutionContext.class);
        verify(memoryCandidateService).extractCandidates(captor.capture(), eq("manual-ref"), eq(3));
        AgentExecutionContext context = captor.getValue();
        assertEquals("t1", context.tenantId());
        assertEquals(7L, context.userId());
        assertEquals("conv-9", context.conversationId());
        assertNotNull(context.executionId());
        assertNotNull(context.requestId());
    }

    @Test
    void extractCandidatesShouldRejectAnonymous() {
        ApiResponse<List<AgentMemoryView>> response = controller.extractCandidates(new AgentMemoryExtractRequest());

        assertEquals(401, response.getCode());
    }

    @Test
    void approveCandidateShouldDelegateToOwnCandidate() {
        TenantContext.setSessionInfo(new SessionInfo("t1", 7L));
        when(memoryCandidateService.approveByUser("t1", 7L, 61L))
                .thenReturn(fact(70L, "PERMANENT", 7L, "候选口径"));

        ApiResponse<AgentMemoryView> response = controller.approveCandidate(61L);

        assertEquals(200, response.getCode());
        assertEquals("候选口径", response.getData().getFactText());
        verify(memoryCandidateService).approveByUser("t1", 7L, 61L);
    }

    @Test
    void rejectCandidateShouldAcceptEmptyBody() {
        TenantContext.setSessionInfo(new SessionInfo("t1", 7L));
        AgentMemoryFact rejected = fact(61L, "PERMANENT", 7L, "候选口径");
        rejected.setStatus("REJECTED");
        when(memoryCandidateService.rejectByUser("t1", 7L, 61L, null)).thenReturn(rejected);

        ApiResponse<AgentMemoryView> response = controller.rejectCandidate(61L, null);

        assertEquals(200, response.getCode());
        assertEquals("候选已拒绝", response.getData().getStatusLabel());
        verify(memoryCandidateService).rejectByUser("t1", 7L, 61L, null);
    }

    @Test
    void rejectCandidateShouldPassReason() {
        TenantContext.setSessionInfo(new SessionInfo("t1", 7L));
        AgentMemoryRejectRequest request = new AgentMemoryRejectRequest();
        request.setReason("口径已调整");
        AgentMemoryFact rejected = fact(61L, "PERMANENT", 7L, "候选口径");
        rejected.setStatus("REJECTED");
        when(memoryCandidateService.rejectByUser("t1", 7L, 61L, "口径已调整")).thenReturn(rejected);

        ApiResponse<AgentMemoryView> response = controller.rejectCandidate(61L, request);

        assertEquals(200, response.getCode());
        verify(memoryCandidateService).rejectByUser("t1", 7L, 61L, "口径已调整");
    }

    @Test
    void exportOwnShouldReturnSessionScope() {
        TenantContext.setSessionInfo(new SessionInfo("t1", 7L));
        when(agentMemoryService.exportOwn("t1", 7L)).thenReturn(new AgentMemoryExportView(
                "t1", 7L, "用户永久记忆与候选（含历史状态）", 1, LocalDateTime.now(),
                List.of(AgentMemoryView.from(fact(80L, "PERMANENT", 7L, "销售额口径为不含税金额")))));

        ApiResponse<AgentMemoryExportView> response = controller.exportOwn();

        assertEquals(200, response.getCode());
        assertEquals(1, response.getData().total());
        assertEquals("t1", response.getData().tenantId());
        assertTrue(response.getData().memories().get(0).getSourceLabel() != null);
        verify(agentMemoryService).exportOwn("t1", 7L);
    }

    @Test
    void exportOwnShouldRejectAnonymous() {
        ApiResponse<AgentMemoryExportView> response = controller.exportOwn();

        assertEquals(401, response.getCode());
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
