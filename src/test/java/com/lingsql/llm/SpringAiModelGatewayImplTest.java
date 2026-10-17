package com.lingsql.llm;

import com.lingsql.agent.ModelHealthService;
import com.lingsql.agent.ModelRouteCandidate;
import com.lingsql.agent.ModelRoutePlan;
import com.lingsql.agent.ModelRouter;
import com.lingsql.agent.model.AiModelConfig;
import com.lingsql.agent.repository.AiModelConfigRepository;
import com.lingsql.billing.invocation.BilledModelResponse;
import com.lingsql.billing.invocation.BillingAccessDeniedException;
import com.lingsql.billing.invocation.BillingInvocationUnknownException;
import com.lingsql.billing.invocation.IPhysicalModelBilling;
import com.lingsql.billing.invocation.IPhysicalModelCall;
import com.lingsql.billing.invocation.PhysicalModelInvocation;
import com.lingsql.billing.invocation.PhysicalModelResponse;
import com.lingsql.common.exception.BizException;
import com.lingsql.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 模型网关故障切换边界测试。
 *
 * <p>覆盖两条必须长期保持的边界：出站前本地准备失败时按同一冻结租户授权计划的优先级顺序回退候选，
 * 每次物理调用独立进入资金状态机；一旦进入 {@code DISPATCHED}（含空响应与出站前资金拒绝），
 * 绝不再切换模型或自动退款。</p>
 *
 * @author 余炘洋
 * @date 2026-10-17
 */
class SpringAiModelGatewayImplTest {

    private static final String TENANT_ID = "tenant-a";
    private static final String CHAT_BASE_URL = "http://127.0.0.1:9999";
    private static final String ROUTE_FINGERPRINT =
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    @Test
    void returnsPrimaryResultWithBillingEvidence() {
        AiModelConfig primary = model(1L, CHAT_BASE_URL);
        Fixture fixture = new Fixture(plan(primary));
        fixture.noAuthClient.respond(1L, "select 1 from t", 21);

        ModelCallResult result = fixture.gateway.chat(command());

        assertEquals("select 1 from t", result.content());
        assertEquals("provider-1", result.providerName());
        assertEquals("model-1", result.modelName());
        assertEquals("version-1", result.modelVersion());
        assertEquals(21, result.tokenCount().intValue());
        assertEquals(7L, result.costAmount().longValue());
        assertEquals("billing-v1", result.billingVersion());
        assertEquals(1, result.routeAttempt());
        assertEquals(List.of(1L), fixture.noAuthClient.invokedModelIds);
        assertEquals(1, fixture.billing.invocations.size());

        PhysicalModelInvocation invocation = fixture.billing.invocations.get(0);
        assertEquals(result.invocationId(), invocation.invocationId());
        assertEquals(TENANT_ID, invocation.tenantId());
        assertEquals("wf-1", invocation.workflowRequestId());
        assertEquals("sql-architect:exec-1", invocation.caller());
        assertEquals("SQL_ARCHITECT", invocation.stage());
        assertEquals(1, invocation.logicalCallNo());
        assertEquals(ROUTE_FINGERPRINT, invocation.routeFingerprint());
        assertTrue(invocation.maxBillableTokens() > 0);
    }

    @Test
    void fallsBackToNextCandidateWhenPrimaryPreparationFails() {
        AiModelConfig brokenPrimary = model(1L, "");
        AiModelConfig backup = model(2L, CHAT_BASE_URL);
        Fixture fixture = new Fixture(plan(brokenPrimary, backup));
        fixture.noAuthClient.respond(2L, "select 2", 15);

        ModelCallResult result = fixture.gateway.chat(command());

        assertEquals("select 2", result.content());
        assertEquals("provider-2", result.providerName());
        assertEquals(2, result.routeAttempt());
        assertEquals(List.of(2L), fixture.noAuthClient.invokedModelIds);
        assertEquals(1, fixture.billing.invocations.size());
        assertEquals(2, fixture.billing.invocations.get(0).routeAttempt());
        assertEquals(2L, fixture.billing.invocations.get(0).modelConfigId());
        assertEquals(1, fixture.healthService.getSnapshot(1L).getFailureCount());
    }

    @Test
    void walksThroughWholeFrozenPlanBeforeGivingUp() {
        AiModelConfig missingBaseUrl = model(1L, "");
        AiModelConfig missingVersion = model(2L, CHAT_BASE_URL);
        missingVersion.setModelVersion(" ");
        AiModelConfig healthy = model(3L, CHAT_BASE_URL);
        Fixture fixture = new Fixture(plan(missingBaseUrl, missingVersion, healthy));
        fixture.noAuthClient.respond(3L, "select 3", 9);

        ModelCallResult result = fixture.gateway.chat(command());

        assertEquals("select 3", result.content());
        assertEquals(3, result.routeAttempt());
        assertEquals(List.of(3L), fixture.noAuthClient.invokedModelIds);
        assertEquals(1, fixture.billing.invocations.size());
        assertEquals(3, fixture.billing.invocations.get(0).routeAttempt());
        assertEquals(1, fixture.healthService.getSnapshot(1L).getFailureCount());
        assertEquals(1, fixture.healthService.getSnapshot(2L).getFailureCount());
    }

    @Test
    void failsClosedWhenEveryCandidateFailsPreparation() {
        Fixture fixture = new Fixture(plan(model(1L, ""), model(2L, ""), model(3L, "")));

        BizException exception = assertThrows(BizException.class, () -> fixture.gateway.chat(command()));

        assertEquals(ErrorCode.MODEL_UNAVAILABLE, exception.getErrorCode());
        assertTrue(fixture.billing.invocations.isEmpty());
        assertTrue(fixture.noAuthClient.invokedModelIds.isEmpty());
        Throwable rootCause = exception.getCause();
        assertNotNull(rootCause);
        assertTrue(rootCause.getSuppressed().length >= 2);
        assertEquals(1, fixture.healthService.getSnapshot(1L).getFailureCount());
        assertEquals(1, fixture.healthService.getSnapshot(2L).getFailureCount());
        assertEquals(1, fixture.healthService.getSnapshot(3L).getFailureCount());
    }

    @Test
    void reportsPreparationFailureWhenNoHealthyCandidateRemains() {
        AiModelConfig brokenPrimary = model(1L, "");
        AiModelConfig fusedBackup = model(2L, CHAT_BASE_URL);
        Fixture fixture = new Fixture(plan(brokenPrimary, fusedBackup));
        fixture.healthService.fuseModel(fusedBackup, "测试预置熔断");

        BizException exception = assertThrows(BizException.class, () -> fixture.gateway.chat(command()));

        assertEquals(ErrorCode.MODEL_UNAVAILABLE, exception.getErrorCode());
        assertTrue(fixture.billing.invocations.isEmpty());
        Throwable rootCause = exception.getCause();
        assertNotNull(rootCause);
        assertTrue(rootCause.getSuppressed().length >= 1);
        assertTrue(rootCause.getSuppressed()[0].getMessage().contains("没有可用模型"));
    }

    @Test
    void fusesModelAfterRepeatedPreparationFailures() {
        AiModelConfig broken = model(1L, "");
        Fixture fixture = new Fixture(plan(broken));

        for (int round = 0; round < 3; round++) {
            assertThrows(BizException.class, () -> fixture.gateway.chat(command()));
        }

        assertEquals(3, fixture.healthService.getSnapshot(1L).getFailureCount());
        assertTrue(fixture.healthService.getSnapshot(1L).isFused());
        assertEquals(0, broken.getIsActive().intValue());
        assertNotNull(broken.getFuseTime());

        assertThrows(BizException.class, () -> fixture.gateway.chat(command()));

        assertTrue(fixture.billing.invocations.isEmpty());
        assertTrue(fixture.noAuthClient.invokedModelIds.isEmpty());
    }

    @Test
    void doesNotSwitchModelAfterDispatchUnknown() {
        AiModelConfig primary = model(1L, CHAT_BASE_URL);
        AiModelConfig backup = model(2L, CHAT_BASE_URL);
        Fixture fixture = new Fixture(plan(primary, backup));
        fixture.noAuthClient.respond(1L, "select 1", 12);
        fixture.billing.postInvocationFailure =
                new BillingInvocationUnknownException("物理模型调用结果不确定，禁止自动退款或切换模型");

        assertThrows(BillingInvocationUnknownException.class, () -> fixture.gateway.chat(command()));

        assertEquals(1, fixture.billing.invocations.size());
        assertEquals(1, fixture.billing.invocations.get(0).routeAttempt());
        assertEquals(List.of(1L), fixture.noAuthClient.invokedModelIds);
        assertEquals(1, fixture.healthService.getSnapshot(1L).getFailureCount());
    }

    @Test
    void doesNotSwitchModelWhenBillingRefusesBeforeDispatch() {
        AiModelConfig primary = model(1L, CHAT_BASE_URL);
        AiModelConfig backup = model(2L, CHAT_BASE_URL);
        Fixture fixture = new Fixture(plan(primary, backup));
        fixture.billing.reservationFailure = new BillingAccessDeniedException("余额不足，请升级套餐");

        assertThrows(BillingAccessDeniedException.class, () -> fixture.gateway.chat(command()));

        assertEquals(1, fixture.billing.invocations.size());
        assertTrue(fixture.noAuthClient.invokedModelIds.isEmpty());
        assertEquals(0, fixture.healthService.getSnapshot(1L).getFailureCount());
    }

    @Test
    void recordsHealthFailureWithoutSwitchingWhenContentIsBlank() {
        AiModelConfig primary = model(1L, CHAT_BASE_URL);
        AiModelConfig backup = model(2L, CHAT_BASE_URL);
        Fixture fixture = new Fixture(plan(primary, backup));
        fixture.noAuthClient.respond(1L, " ", 12);

        BizException exception = assertThrows(BizException.class, () -> fixture.gateway.chat(command()));

        assertEquals(ErrorCode.MODEL_UNAVAILABLE, exception.getErrorCode());
        assertEquals(1, fixture.billing.invocations.size());
        assertEquals(List.of(1L), fixture.noAuthClient.invokedModelIds);
        assertEquals(1, fixture.healthService.getSnapshot(1L).getFailureCount());
    }

    @Test
    void failsClosedWhenTenantHasNoRoutePlan() {
        Fixture fixture = new Fixture(new BizException(ErrorCode.MODEL_UNAVAILABLE, "当前租户未配置模型授权路由"));

        BizException exception = assertThrows(BizException.class, () -> fixture.gateway.chat(command()));

        assertEquals(ErrorCode.MODEL_UNAVAILABLE, exception.getErrorCode());
        assertTrue(fixture.billing.invocations.isEmpty());
        assertTrue(fixture.noAuthClient.invokedModelIds.isEmpty());
    }

    private static ModelCallCommand command() {
        return new ModelCallCommand(TENANT_ID, "wf-1", "sql-architect", "exec-1", "SQL_ARCHITECT", 1,
                "系统提示词", "最近一个月销售额");
    }

    private static AiModelConfig model(long id, String baseUrl) {
        AiModelConfig config = new AiModelConfig();
        config.setId(id);
        config.setProviderName("provider-" + id);
        config.setModelName("model-" + id);
        config.setModelVersion("version-" + id);
        config.setBaseUrl(baseUrl);
        config.setWeight(10);
        config.setIsActive(1);
        config.setLastProbeStatus("HEALTHY");
        return config;
    }

    private static ModelRoutePlan plan(AiModelConfig... models) {
        List<ModelRouteCandidate> candidates = new ArrayList<>();
        for (int index = 0; index < models.length; index++) {
            candidates.add(new ModelRouteCandidate(models[index], index + 1, 10));
        }
        return new ModelRoutePlan(TENANT_ID, ROUTE_FINGERPRINT, candidates);
    }

    /**
     * 测试夹具：使用真实模型健康状态与真实路由选择语义，只替换数据库、传输和资金持久化。
     *
     * @author 余炘洋
     * @date 2026-10-17
     */
    private static final class Fixture {

        private final ModelHealthService healthService =
                new ModelHealthService(Mockito.mock(AiModelConfigRepository.class));
        private final FakeNoAuthClient noAuthClient = new FakeNoAuthClient();
        private final FakePhysicalBilling billing = new FakePhysicalBilling();
        private final SpringAiModelGatewayImpl gateway;

        private Fixture(ModelRoutePlan routePlan) {
            this.gateway = new SpringAiModelGatewayImpl(
                    new FixedPlanRouter(routePlan, healthService), healthService, noAuthClient,
                    new OpenAiEndpointResolver(), billing);
        }

        private Fixture(BizException planFailure) {
            this.gateway = new SpringAiModelGatewayImpl(
                    new FixedPlanRouter(planFailure, healthService), healthService, noAuthClient,
                    new OpenAiEndpointResolver(), billing);
        }
    }

    /**
     * 固定计划路由器：仍使用真实的选择、优先级和排除语义，只固定租户计划来源。
     *
     * @author 余炘洋
     * @date 2026-10-17
     */
    private static final class FixedPlanRouter extends ModelRouter {

        private final ModelRoutePlan routePlan;
        private final BizException planFailure;

        private FixedPlanRouter(ModelRoutePlan routePlan, ModelHealthService healthService) {
            super(null, healthService);
            this.routePlan = routePlan;
            this.planFailure = null;
        }

        private FixedPlanRouter(BizException planFailure, ModelHealthService healthService) {
            super(null, healthService);
            this.routePlan = null;
            this.planFailure = planFailure;
        }

        @Override
        public ModelRoutePlan planFor(String tenantId) {
            if (planFailure != null) {
                throw planFailure;
            }
            return routePlan;
        }
    }

    /**
     * 无鉴权传输假件：按模型 ID 返回预置响应，用于区分出站前失败与派发后失败。
     *
     * @author 余炘洋
     * @date 2026-10-17
     */
    private static final class FakeNoAuthClient extends OpenAiCompatibleNoAuthClient {

        private final Map<Long, ModelResponse> responses = new HashMap<>();
        private final List<Long> invokedModelIds = new ArrayList<>();

        private FakeNoAuthClient() {
            super(null, null, null);
        }

        private void respond(Long modelId, String content, int tokenCount) {
            responses.put(modelId, new ModelResponse(content, tokenCount));
        }

        @Override
        ModelResponse chat(AiModelConfig modelConfig, String systemPrompt, String userPrompt) {
            invokedModelIds.add(modelConfig.getId());
            ModelResponse response = responses.get(modelConfig.getId());
            if (response == null) {
                throw new IllegalStateException("测试未准备模型响应: " + modelConfig.getId());
            }
            return response;
        }
    }

    /**
     * 资金状态机假件：保持“预留 → 派发 → 物理调用 → 结算”的顺序，并模拟出站前资金拒绝与派发后不确定。
     *
     * @author 余炘洋
     * @date 2026-10-17
     */
    private static final class FakePhysicalBilling implements IPhysicalModelBilling {

        private final List<PhysicalModelInvocation> invocations = new ArrayList<>();
        private RuntimeException reservationFailure;
        private RuntimeException postInvocationFailure;

        @Override
        public <T> BilledModelResponse<T> execute(PhysicalModelInvocation invocation,
                                                  IPhysicalModelCall<T> physicalCall) {
            invocations.add(invocation);
            if (reservationFailure != null) {
                throw reservationFailure;
            }
            PhysicalModelResponse<T> modelResponse = physicalCall.invoke();
            if (postInvocationFailure != null) {
                throw postInvocationFailure;
            }
            if (modelResponse.actualTokens() == null) {
                throw new BillingInvocationUnknownException("供应商未返回可信用量，调用已进入人工核对");
            }
            return new BilledModelResponse<>(modelResponse.payload(), invocation.invocationId(),
                    "reservation-" + invocations.size(), modelResponse.actualTokens(), 7L, "billing-v1");
        }
    }
}
