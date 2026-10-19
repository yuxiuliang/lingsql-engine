package com.lingsql.agent.knowledge;

import com.lingsql.agent.orchestration.AgentExecutionContext;
import com.lingsql.agent.plugin.AgentPluginException;
import com.lingsql.agent.plugin.AgentPluginExecutor;
import com.lingsql.agent.plugin.AgentPluginFailureReason;
import com.lingsql.agent.plugin.KnowledgeRetrievePlugin;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * 知识上下文服务单元测试：受控策略、上下文复用、失败降级与无知识占位。
 *
 * @author 余炘洋
 * @date 2026-10-19
 */
class KnowledgeContextServiceTest {

    private final AgentPluginExecutor pluginExecutor = Mockito.mock(AgentPluginExecutor.class);

    private final KnowledgeContextService service =
            new KnowledgeContextService(stubProvider(pluginExecutor));

    /**
     * 构造仅提供指定网关的 {@link ObjectProvider}，模拟 Spring 的延迟解析。
     *
     * @param executor 插件网关，可为 null
     * @return 对象提供者
     * @author 余炘洋
     * @date 2026-10-19
     */
    private static ObjectProvider<AgentPluginExecutor> stubProvider(AgentPluginExecutor executor) {
        return new ObjectProvider<>() {
            @Override
            public AgentPluginExecutor getObject() {
                return executor;
            }

            @Override
            public AgentPluginExecutor getIfAvailable() {
                return executor;
            }
        };
    }

    private AgentExecutionContext context() {
        return new AgentExecutionContext("exec-1", "req-1", "tenant-1", 7L, "conv-1");
    }

    private void stubResult(KnowledgeRetrievePlugin.Result result) {
        when(pluginExecutor.execute(
                eq(KnowledgeRetrievePlugin.PLUGIN_ID),
                any(),
                any(),
                any(KnowledgeRetrievePlugin.Request.class),
                eq(KnowledgeRetrievePlugin.Result.class)))
                .thenReturn(result);
    }

    @Test
    void shouldRenderTraceableKnowledgeWithSourceLabels() {
        stubResult(new KnowledgeRetrievePlugin.Result(true, "OK", List.of(
                new KnowledgeRetrievePlugin.KnowledgeHit(
                        "7", "销售额口径", "1.0", "DB_CORPUS", "按自然月统计"))));

        String rendered = service.buildContext(context(), " 上月销售额怎么算 ");

        assertTrue(rendered.startsWith("[1] 销售额口径（版本 1.0）（来源 DB_CORPUS，文档 7）：按自然月统计"));
        assertTrue(KnowledgeReferenceRenderer.hasKnowledge(rendered));
        ArgumentCaptor<KnowledgeRetrievePlugin.Request> request =
                ArgumentCaptor.forClass(KnowledgeRetrievePlugin.Request.class);
        ArgumentCaptor<AgentExecutionContext> execContext = ArgumentCaptor.forClass(AgentExecutionContext.class);
        Mockito.verify(pluginExecutor).execute(
                eq(KnowledgeRetrievePlugin.PLUGIN_ID),
                execContext.capture(),
                any(),
                request.capture(),
                eq(KnowledgeRetrievePlugin.Result.class));
        // 说明：检索必须复用本次运行上下文，保证调用 Outbox 可按 executionId 追溯。
        assertEquals("exec-1", execContext.getValue().executionId());
        assertEquals("tenant-1", execContext.getValue().tenantId());
        assertEquals("上月销售额怎么算", request.getValue().query());
        assertEquals(KnowledgeContextService.RETRIEVE_TOP_K, request.getValue().maxResults());
    }

    @Test
    void shouldOnlyAllowKnowledgePluginInRetrievalPolicy() {
        stubResult(new KnowledgeRetrievePlugin.Result(false, "NO_HIT", List.of()));

        service.buildContext(context(), "口径");

        assertEquals(List.of(KnowledgeRetrievePlugin.PLUGIN_ID), service.allowedPluginIds());
    }

    @Test
    void shouldDegradeToNoKnowledgeWhenTenantNotGranted() {
        when(pluginExecutor.execute(
                eq(KnowledgeRetrievePlugin.PLUGIN_ID),
                any(),
                any(),
                any(KnowledgeRetrievePlugin.Request.class),
                eq(KnowledgeRetrievePlugin.Result.class)))
                .thenThrow(new AgentPluginException(AgentPluginFailureReason.POLICY_DENIED, "租户未授权插件: knowledge_retrieve"));

        // 说明：未授权租户必须 fail-closed 降级，不能阻断受控 SQL 生成。
        String rendered = service.buildContext(context(), "口径");

        assertEquals(KnowledgeReferenceRenderer.NO_KNOWLEDGE, rendered);
        assertFalse(KnowledgeReferenceRenderer.hasKnowledge(rendered));
    }

    @Test
    void shouldDegradeWhenProviderFails() {
        when(pluginExecutor.execute(anyString(), any(), any(), any(), eq(KnowledgeRetrievePlugin.Result.class)))
                .thenThrow(new IllegalStateException("向量库不可用"));

        assertEquals(KnowledgeReferenceRenderer.NO_KNOWLEDGE, service.buildContext(context(), "口径"));
    }

    @Test
    void shouldReturnNoKnowledgeWithoutContextOrQuestion() {
        assertEquals(KnowledgeReferenceRenderer.NO_KNOWLEDGE, service.buildContext(null, "口径"));
        assertEquals(KnowledgeReferenceRenderer.NO_KNOWLEDGE, service.buildContext(context(), "  "));
        Mockito.verifyNoInteractions(pluginExecutor);
    }

    @Test
    void shouldReturnNoKnowledgeWhenNoHit() {
        stubResult(new KnowledgeRetrievePlugin.Result(false, "NO_HIT", List.of()));

        assertEquals(KnowledgeReferenceRenderer.NO_KNOWLEDGE, service.buildContext(context(), "口径"));
    }

    @Test
    void shouldDegradeWhenPluginGatewayUnavailable() {
        // 说明：网关未装配（或延迟解析不到）时必须降级，不能抛出异常中断 SQL 生成。
        KnowledgeContextService degraded = new KnowledgeContextService(stubProvider(null));

        assertEquals(KnowledgeReferenceRenderer.NO_KNOWLEDGE, degraded.buildContext(context(), "口径"));
    }
}
