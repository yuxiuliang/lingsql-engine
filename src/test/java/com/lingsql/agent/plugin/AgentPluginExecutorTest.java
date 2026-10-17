package com.lingsql.agent.plugin;

import com.lingsql.agent.orchestration.AgentCallerType;
import com.lingsql.agent.orchestration.AgentExecutionContext;
import com.lingsql.agent.orchestration.AgentRunControl;
import com.lingsql.agent.orchestration.AgentRunPolicy;
import com.lingsql.agent.service.AgentPluginGrantService;
import com.lingsql.agent.service.PluginInvocationOutboxService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 插件执行网关租户授权强制测试：受控业务插件未授权必须 fail-closed。
 *
 * @author 余炘洋
 * @date 2026-10-17
 */
class AgentPluginExecutorTest {

    private static final AgentExecutionContext CONTEXT =
            new AgentExecutionContext("exec-1", "req-1", "tenant-a", 1L, "conv-1");

    private final AgentPluginGrantService grantService = mock(AgentPluginGrantService.class);
    private final PluginInvocationOutboxService outboxService = mock(PluginInvocationOutboxService.class);

    private final StubPlugin orchestratedPlugin = new StubPlugin(new AgentPluginDescriptor(
            "granted_tool", "1.0.0", AgentPluginAccess.ORCHESTRATED, AgentPluginCost.LOW, true, "受控业务插件"));
    private final StubPlugin systemPlugin = new StubPlugin(new AgentPluginDescriptor(
            "system_tool", "1.0.0", AgentPluginAccess.SYSTEM_ONLY, AgentPluginCost.HIGH, false, "系统插件"));

    private final AgentPluginExecutor executor = new AgentPluginExecutor(
            new AgentPluginRegistry(List.of(orchestratedPlugin, systemPlugin)), outboxService, grantService);

    @Test
    void orchestratedPluginWithoutGrantShouldFailClosed() {
        when(grantService.isGranted("tenant-a", "granted_tool")).thenReturn(false);

        AgentPluginException exception = assertThrows(AgentPluginException.class,
                () -> executor.execute("granted_tool", CONTEXT, control("granted_tool"), "payload", String.class));

        assertEquals(AgentPluginFailureReason.POLICY_DENIED, exception.getReason());
        assertEquals(0, orchestratedPlugin.invocations.get());
        verify(outboxService, never()).record(anyString(), anyString(), anyString(), anyString(), anyInt());
    }

    @Test
    void orchestratedPluginWithGrantShouldExecute() {
        when(grantService.isGranted("tenant-a", "granted_tool")).thenReturn(true);

        String result = executor.execute("granted_tool", CONTEXT, control("granted_tool"), "payload", String.class);

        assertEquals("echo:payload", result);
        assertEquals(1, orchestratedPlugin.invocations.get());
        verify(outboxService).record(eq("tenant-a"), eq("exec-1"), eq("granted_tool"), eq("SUCCEEDED"), anyInt());
    }

    @Test
    void systemOnlyPluginShouldNotRequireTenantGrant() {
        String result = executor.execute("system_tool", CONTEXT, control("system_tool"), "payload", String.class);

        assertEquals("echo:payload", result);
        verify(outboxService).record(eq("tenant-a"), eq("exec-1"), eq("system_tool"), eq("SUCCEEDED"), anyInt());
    }

    private AgentRunControl control(String pluginId) {
        return new AgentRunControl(new AgentRunPolicy(Set.of(pluginId), 1, AgentCallerType.SYSTEM, 3, 3, 10_000L));
    }

    /**
     * 测试替身插件：只回显输入，用于验证网关判定与调用次数。
     */
    private static final class StubPlugin implements IAgentPlugin<String, String> {

        private final AgentPluginDescriptor descriptor;
        private final AtomicInteger invocations = new AtomicInteger();

        private StubPlugin(AgentPluginDescriptor descriptor) {
            this.descriptor = descriptor;
        }

        @Override
        public AgentPluginDescriptor descriptor() {
            return descriptor;
        }

        @Override
        public Class<String> inputType() {
            return String.class;
        }

        @Override
        public Class<String> outputType() {
            return String.class;
        }

        @Override
        public String execute(AgentExecutionContext context, AgentRunControl control, String input) {
            invocations.incrementAndGet();
            return "echo:" + input;
        }
    }
}
