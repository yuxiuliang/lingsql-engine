package com.lingsql.llm;

import com.lingsql.agent.ModelHealthService;
import com.lingsql.agent.ModelRouter;
import com.lingsql.agent.ModelRouteSelection;
import com.lingsql.agent.ModelRoutePlan;
import com.lingsql.agent.model.AiModelConfig;
import com.lingsql.billing.invocation.BilledModelResponse;
import com.lingsql.billing.invocation.BillingInvocationUnknownException;
import com.lingsql.billing.invocation.IPhysicalModelBilling;
import com.lingsql.billing.invocation.PhysicalModelInvocation;
import com.lingsql.billing.invocation.PhysicalModelResponse;
import com.lingsql.common.exception.BizException;
import com.lingsql.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 私有部署大模型客户端，通过 Spring AI 调用 OpenAI 兼容模型。
 *
 * @author 余炘洋
 * @date 2026-01-22
 * @modify 余炘洋 2026-08-16 主备模型复用同一租户授权计划
 * @modify 余炘洋 2026-10-17 故障切换只在出站前按计划优先级回退候选
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SpringAiModelGatewayImpl implements IModelGateway {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(60);
    private static final double DEFAULT_TEMPERATURE = 0.0d;
    private static final int DEFAULT_MAX_TOKENS = 512;

    private final ModelRouter modelRouter;
    private final ModelHealthService healthService;
    private final OpenAiCompatibleNoAuthClient noAuthClient;
    private final OpenAiEndpointResolver endpointResolver;
    private final IPhysicalModelBilling physicalModelBilling;
    private final ConcurrentHashMap<Long, SpringAiModelHolder> modelCache = new ConcurrentHashMap<>();

    /**
     * 发送聊天请求。
     *
     * <p>故障切换只发生在出站前：模型配置、客户端或地址等本地准备失败时，按同一冻结租户授权计划的
     * 优先级顺序回退到后续候选；每次物理调用都独立预留、派发、结算并使用独立 {@code invocationId}。
     * 一旦进入 {@code DISPATCHED}，失败只进入资金状态机并记录健康惩罚，不再切换模型。</p>
     *
     * @param command 显式模型调用命令
     * @return 模型调用结果
     * @author 余炘洋
     * @date 2026-01-22
     */
    @Override
    public ModelCallResult chat(ModelCallCommand command) {
        ModelRoutePlan routePlan = planRoute(command);
        List<Long> failedModelIds = new ArrayList<>();
        List<LocalModelPreparationException> preparationFailures = new ArrayList<>();
        int maxAttempts = Math.max(1, routePlan.candidates().size());
        for (int routeAttempt = 1; routeAttempt <= maxAttempts; routeAttempt++) {
            ModelRouteSelection selection = selectCandidate(routePlan, failedModelIds, preparationFailures);
            AiModelConfig candidate = selection.modelConfig();
            PreparedModelCall prepared;
            try {
                prepared = preparePhysicalCall(candidate, command);
            } catch (LocalModelPreparationException exception) {
                healthService.recordFailure(candidate, exception.getMessage());
                log.warn("模型出站前准备失败，改用同一冻结计划内的下一个候选: routeAttempt={}, id={}, provider={}, model={}",
                        routeAttempt, candidate.getId(), candidate.getProviderName(), candidate.getModelName(), exception);
                failedModelIds.add(candidate.getId());
                preparationFailures.add(exception);
                continue;
            }
            return executePhysicalCall(command, selection, prepared, routeAttempt);
        }
        throw allCandidatesUnavailable(preparationFailures, null);
    }

    /**
     * 冻结租户当前可调度的模型授权计划；未授权或计划不可用时失败关闭。
     *
     * @param command 显式模型调用命令
     * @return 不可变路由计划
     */
    private ModelRoutePlan planRoute(ModelCallCommand command) {
        try {
            return modelRouter.planFor(command.tenantId());
        } catch (Exception exception) {
            throw modelUnavailable("未找到可用的模型授权路由计划", exception);
        }
    }

    /**
     * 从同一冻结计划中选择下一个候选，已失败的候选不再参与选择。
     *
     * @param routePlan 冻结路由计划
     * @param failedModelIds 已失败并排除的模型 ID
     * @param preparationFailures 已发生的出站前准备失败
     * @return 带路由证据的选择结果
     */
    private ModelRouteSelection selectCandidate(ModelRoutePlan routePlan,
                                                List<Long> failedModelIds,
                                                List<LocalModelPreparationException> preparationFailures) {
        try {
            return modelRouter.selectFromPlan(routePlan, failedModelIds);
        } catch (BizException exception) {
            if (preparationFailures.isEmpty()) {
                throw modelUnavailable("未找到可用的主模型", exception);
            }
            throw allCandidatesUnavailable(preparationFailures, exception);
        }
    }

    /**
     * 汇总计划内候选全部不可用的证据：首个失败作为根因，其余准备失败和选择失败挂为抑制异常。
     *
     * @param preparationFailures 出站前准备失败
     * @param selectionFailure 计划内已无健康候选时的选择失败，可为空
     * @return 对外统一的模型不可用异常
     */
    private BizException allCandidatesUnavailable(List<LocalModelPreparationException> preparationFailures,
                                                  BizException selectionFailure) {
        LocalModelPreparationException rootCause = preparationFailures.get(0);
        for (int index = 1; index < preparationFailures.size(); index++) {
            rootCause.addSuppressed(preparationFailures.get(index));
        }
        if (selectionFailure != null) {
            rootCause.addSuppressed(selectionFailure);
        }
        return modelUnavailable("已冻结模型路由计划内的候选均不可用（出站前准备失败）", rootCause);
    }

    /**
     * 在持久计费保护下执行一次物理模型调用。
     *
     * @param modelConfig  模型配置
     * @param command 模型调用命令
     * @param prepared 已完成本地校验的调用
     * @param routeAttempt 物理路由序号
     * @return 模型调用结果
     * @author 余炘洋
     * @date 2026-01-22
     */
    private ModelCallResult executePhysicalCall(
            ModelCallCommand command,
            ModelRouteSelection selection,
            PreparedModelCall prepared,
            int routeAttempt
    ) {
        AiModelConfig modelConfig = selection.modelConfig();
        long startTime = System.currentTimeMillis();
        String promptHash = hashPrompt(command.systemPrompt(), command.userPrompt());
        String invocationId = buildInvocationId(
                command, modelConfig, selection.routeFingerprint(), routeAttempt, promptHash);
        PhysicalModelInvocation invocation = new PhysicalModelInvocation(
                invocationId,
                command.tenantId(),
                command.workflowRequestId(),
                command.logicalCallNo(),
                command.stage(),
                command.callerType() + ":" + command.callerId(),
                routeAttempt,
                modelConfig.getId(),
                modelConfig.getProviderName(),
                modelConfig.getModelName(),
                modelConfig.getModelVersion(),
                selection.routeFingerprint(),
                resolveMaximumBillableTokens(command),
                promptHash
        );
        BilledModelResponse<ModelResponse> billedResponse;
        try {
            billedResponse = physicalModelBilling.execute(
                    invocation,
                    () -> {
                        ModelResponse response = prepared.invoke();
                        return new PhysicalModelResponse<>(response, response.tokenCount(), null);
                    }
            );
        } catch (BillingInvocationUnknownException exception) {
            // 派发后结果不确定：只记录健康惩罚和熔断证据，禁止在同一逻辑调用内切换模型或自动退款。
            healthService.recordFailure(modelConfig, exception.getMessage());
            throw exception;
        }
        ModelResponse modelResponse = billedResponse.payload();
        String content = modelResponse.content();
        if (content == null || content.isBlank()) {
            // 空响应已经完成结算，属于派发后失败：不切换模型，只记录健康证据影响后续调用的候选顺序。
            healthService.recordFailure(modelConfig, "模型返回为空");
            throw new BizException(ErrorCode.MODEL_UNAVAILABLE, "模型返回为空");
        }

        long costMs = System.currentTimeMillis() - startTime;
        healthService.recordSuccess(modelConfig, costMs);
        log.info("模型调用成功: id={}, provider={}, model={}, costMs={}",
                modelConfig.getId(),
                modelConfig.getProviderName(),
                modelConfig.getModelName(),
                costMs);
        return new ModelCallResult(
                content,
                modelConfig.getProviderName(),
                modelConfig.getModelName(),
                modelConfig.getModelVersion(),
                billedResponse.actualTokens(),
                billedResponse.actualAmount(),
                billedResponse.invocationId(),
                billedResponse.billingVersion(),
                routeAttempt
        );
    }

    private PreparedModelCall preparePhysicalCall(AiModelConfig modelConfig, ModelCallCommand command) {
        validateModelConfig(modelConfig);
        try {
            if (hasApiKey(modelConfig)) {
                OpenAiChatModel chatModel = resolveChatModel(modelConfig);
                return () -> requestWithSpringAi(chatModel, command.systemPrompt(), command.userPrompt());
            }
            endpointResolver.resolveApiBaseUrl(modelConfig.getBaseUrl());
            return () -> noAuthClient.chat(modelConfig, command.systemPrompt(), command.userPrompt());
        } catch (Exception exception) {
            throw new LocalModelPreparationException("模型客户端本地初始化失败", exception);
        }
    }

    private ModelResponse requestWithSpringAi(
            OpenAiChatModel chatModel,
            String systemPrompt,
            String userPrompt
    ) {
        ChatResponse response = chatModel.call(new Prompt(List.of(
                new SystemMessage(systemPrompt),
                new UserMessage(userPrompt)
        )));
        String content = response == null || response.getResult() == null
                ? null
                : response.getResult().getOutput().getText();
        return new ModelResponse(content, resolveTokenCount(response));
    }

    private void validateModelConfig(AiModelConfig modelConfig) {
        if (modelConfig == null
                || modelConfig.getId() == null
                || modelConfig.getProviderName() == null
                || modelConfig.getProviderName().isBlank()
                || modelConfig.getModelName() == null
                || modelConfig.getModelName().isBlank()
                || modelConfig.getModelVersion() == null
                || modelConfig.getModelVersion().isBlank()
                || modelConfig.getBaseUrl() == null
                || modelConfig.getBaseUrl().isBlank()) {
            throw new LocalModelPreparationException("模型配置不完整");
        }
    }

    private int resolveMaximumBillableTokens(ModelCallCommand command) {
        long promptBytes = command.systemPrompt().getBytes(StandardCharsets.UTF_8).length
                + (long) command.userPrompt().getBytes(StandardCharsets.UTF_8).length;
        return (int) Math.min(Integer.MAX_VALUE, promptBytes + DEFAULT_MAX_TOKENS);
    }

    private String hashPrompt(String systemPrompt, String userPrompt) {
        return hashText(systemPrompt + "\u0000" + userPrompt);
    }

    private String buildInvocationId(
            ModelCallCommand command,
            AiModelConfig modelConfig,
            String routeFingerprint,
            int routeAttempt,
            String promptHash
    ) {
        return hashText(String.join("|",
                command.tenantId(),
                command.workflowRequestId(),
                command.callerType(),
                command.callerId(),
                command.stage(),
                String.valueOf(command.logicalCallNo()),
                String.valueOf(routeAttempt),
                String.valueOf(modelConfig.getId()),
                modelConfig.getModelVersion().trim(),
                routeFingerprint,
                promptHash
        ));
    }

    private BizException modelUnavailable(String message, Throwable cause) {
        if (cause == null) {
            log.error(message);
        } else {
            log.error("{}: {}", message, cause.getMessage(), cause);
        }
        return new BizException(ErrorCode.MODEL_UNAVAILABLE, "模型调用失败，请稍后重试", cause);
    }

    /**
     * 获取模型对应的 Spring AI ChatModel。
     *
     * @param modelConfig 模型配置
     * @return ChatModel
     * @author 余炘洋
     * @date 2026-08-15
     */
    private OpenAiChatModel resolveChatModel(AiModelConfig modelConfig) {
        if (modelConfig == null || modelConfig.getId() == null) {
            return buildChatModel(modelConfig);
        }
        String fingerprint = buildFingerprint(modelConfig);
        SpringAiModelHolder holder = modelCache.compute(modelConfig.getId(), (ignored, current) -> {
            if (current != null && current.fingerprint().equals(fingerprint)) {
                return current;
            }
            return new SpringAiModelHolder(fingerprint, buildChatModel(modelConfig));
        });
        return holder.chatModel();
    }

    /**
     * 根据动态模型配置构建 Spring AI ChatModel。
     *
     * @param modelConfig 模型配置
     * @return ChatModel
     * @author 余炘洋
     * @date 2026-08-15
     */
    private OpenAiChatModel buildChatModel(AiModelConfig modelConfig) {
        if (modelConfig == null || modelConfig.getModelName() == null || modelConfig.getModelName().isBlank()) {
            throw new BizException(ErrorCode.CONFIG_INVALID, "模型名称不能为空");
        }
        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .baseUrl(endpointResolver.resolveApiBaseUrl(modelConfig.getBaseUrl()))
                .apiKey(resolveApiKey(modelConfig.getApiKey()))
                .model(modelConfig.getModelName().trim())
                .temperature(DEFAULT_TEMPERATURE)
                .maxTokens(DEFAULT_MAX_TOKENS)
                .timeout(DEFAULT_TIMEOUT)
                .maxRetries(0)
                .build();
        return OpenAiChatModel.builder()
                .options(options)
                .build();
    }

    /**
     * 获取模型调用 Token 总量。
     *
     * @param response Spring AI 模型响应
     * @return Token 总量
     * @author 余炘洋
     * @date 2026-08-15
     */
    private Integer resolveTokenCount(ChatResponse response) {
        if (response == null || response.getMetadata() == null || response.getMetadata().getUsage() == null) {
            return null;
        }
        return response.getMetadata().getUsage().getTotalTokens();
    }

    private String resolveApiKey(String apiKey) {
        return apiKey.trim();
    }

    private boolean hasApiKey(AiModelConfig modelConfig) {
        return modelConfig != null && modelConfig.getApiKey() != null && !modelConfig.getApiKey().isBlank();
    }

    private String buildFingerprint(AiModelConfig modelConfig) {
        return String.join("|",
                Objects.toString(modelConfig.getBaseUrl(), ""),
                Objects.toString(modelConfig.getModelName(), ""),
                Objects.toString(modelConfig.getModelVersion(), ""),
                hashSecret(Objects.toString(modelConfig.getApiKey(), ""))
        );
    }

    private String hashSecret(String secret) {
        return hashText(secret);
    }

    private String hashText(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JDK 不支持 SHA-256", exception);
        }
    }

    /**
     * 动态模型实例缓存项。
     *
     * @param fingerprint 配置指纹
     * @param chatModel Spring AI 模型
     * @author 余炘洋
     * @date 2026-08-15
     */
    private record SpringAiModelHolder(String fingerprint, OpenAiChatModel chatModel) {
    }

    @FunctionalInterface
    private interface PreparedModelCall {

        ModelResponse invoke();
    }

    private static final class LocalModelPreparationException extends RuntimeException {

        private LocalModelPreparationException(String message) {
            super(message);
        }

        private LocalModelPreparationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
