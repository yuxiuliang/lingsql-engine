package com.lingsql.llm;

import com.lingsql.agent.ModelHealthService;
import com.lingsql.agent.ModelRouter;
import com.lingsql.agent.ModelRouteSelection;
import com.lingsql.agent.model.AiModelConfig;
import com.lingsql.billing.invocation.BilledModelResponse;
import com.lingsql.billing.invocation.BillingInvocationUnknownException;
import com.lingsql.billing.invocation.IPhysicalModelBilling;
import com.lingsql.billing.invocation.PhysicalModelInvocation;
import com.lingsql.billing.invocation.PhysicalModelResponse;
import com.lingsql.billing.invocation.PhysicalBillingException;
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
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 私有部署大模型客户端，通过 Spring AI 调用 OpenAI 兼容模型。
 *
 * @author 余炘洋
 * @date 2026-01-22
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
     * @param command 显式模型调用命令
     * @return 模型调用结果
     * @author 余炘洋
     * @date 2026-01-22
     */
    @Override
    public ModelCallResult chat(ModelCallCommand command) {
        ModelRouteSelection primarySelection;
        try {
            primarySelection = modelRouter.selectModelWithEvidence();
        } catch (Exception exception) {
            throw modelUnavailable("未找到可用的主模型", exception);
        }
        AiModelConfig primary = primarySelection.modelConfig();

        try {
            PreparedModelCall prepared = preparePhysicalCall(primary, command);
            return executePhysicalCall(command, primarySelection, prepared, 1);
        } catch (LocalModelPreparationException exception) {
            healthService.recordFailure(primary, exception.getMessage());
            log.warn("主模型本地配置无效，准备选择备用模型: id={}, provider={}, model={}",
                    primary.getId(), primary.getProviderName(), primary.getModelName(), exception);
            return executeFallback(command, primary, exception);
        }
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
            healthService.recordFailure(modelConfig, exception.getMessage());
            throw exception;
        }
        ModelResponse modelResponse = billedResponse.payload();
        String content = modelResponse.content();
        if (content == null || content.isBlank()) {
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

    private ModelCallResult executeFallback(
            ModelCallCommand command,
            AiModelConfig primary,
            LocalModelPreparationException primaryFailure
    ) {
        try {
            ModelRouteSelection fallbackSelection = modelRouter.selectModelWithEvidence(List.of(primary.getId()));
            AiModelConfig fallback = fallbackSelection.modelConfig();
            PreparedModelCall prepared = preparePhysicalCall(fallback, command);
            return executePhysicalCall(command, fallbackSelection, prepared, 2);
        } catch (LocalModelPreparationException exception) {
            log.error("备用模型本地配置无效: primaryId={}, reason={}",
                    primary.getId(), exception.getMessage(), exception);
            exception.addSuppressed(primaryFailure);
            throw modelUnavailable("主备模型本地配置均不可用", exception);
        } catch (PhysicalBillingException exception) {
            throw exception;
        } catch (BizException exception) {
            throw exception;
        } catch (Exception exception) {
            throw modelUnavailable("备用模型选择失败", exception);
        }
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
        log.error("{}: {}", message, cause.getMessage(), cause);
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
