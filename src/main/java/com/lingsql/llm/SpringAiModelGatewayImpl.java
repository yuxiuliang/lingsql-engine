package com.lingsql.llm;

import com.lingsql.agent.ModelHealthService;
import com.lingsql.agent.ModelRouter;
import com.lingsql.agent.model.AiModelConfig;
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
    private static final String OPENAI_API_PATH = "/v1";
    private static final String OPENAI_CHAT_PATH = "/v1/chat/completions";
    private static final double DEFAULT_TEMPERATURE = 0.0d;
    private static final int DEFAULT_MAX_TOKENS = 512;

    private final ModelRouter modelRouter;
    private final ModelHealthService healthService;
    private final OpenAiCompatibleNoAuthClient noAuthClient;
    private final ConcurrentHashMap<Long, SpringAiModelHolder> modelCache = new ConcurrentHashMap<>();

    /**
     * 发送聊天请求。
     *
     * @param systemPrompt 系统提示词
     * @param userPrompt   用户输入
     * @return 模型输出
     * @author 余炘洋
     * @date 2026-01-22
     */
    @Override
    public String chat(String systemPrompt, String userPrompt) {
        AiModelConfig primary = modelRouter.selectModel();
        try {
            return requestOnce(primary, systemPrompt, userPrompt);
        } catch (Exception ex) {
            healthService.recordFailure(primary, ex.getMessage());
            log.warn("主模型调用失败，准备切换模型: id={}, provider={}, model={}",
                    primary.getId(),
                    primary.getProviderName(),
                    primary.getModelName(), ex);
        }

        try {
            AiModelConfig fallback = modelRouter.selectModel(List.of(primary.getId()));
            try {
                return requestOnce(fallback, systemPrompt, userPrompt);
            } catch (Exception ex) {
                healthService.recordFailure(fallback, ex.getMessage());
                log.error("模型重试失败: id={}, provider={}, model={}, reason={}",
                        fallback.getId(),
                        fallback.getProviderName(),
                        fallback.getModelName(),
                        ex.getMessage(), ex);
                throw ex;
            }
        } catch (Exception ex) {
            log.error("模型切换失败: {}", ex.getMessage(), ex);
            throw new BizException(ErrorCode.MODEL_UNAVAILABLE, "模型调用失败，请稍后重试", ex);
        }
    }

    /**
     * 单次模型调用。
     *
     * @param modelConfig  模型配置
     * @param systemPrompt 系统提示词
     * @param userPrompt   用户输入
     * @return 模型输出
     * @author 余炘洋
     * @date 2026-01-22
     */
    private String requestOnce(AiModelConfig modelConfig, String systemPrompt, String userPrompt) {
        long startTime = System.currentTimeMillis();
        ModelResponse modelResponse = hasApiKey(modelConfig)
                ? requestWithSpringAi(modelConfig, systemPrompt, userPrompt)
                : noAuthClient.chat(modelConfig, systemPrompt, userPrompt);
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
        LlmTraceInfo traceInfo = new LlmTraceInfo();
        traceInfo.setProviderName(modelConfig.getProviderName());
        traceInfo.setModelName(modelConfig.getModelName());
        traceInfo.setModelVersion(modelConfig.getModelVersion());
        traceInfo.setCostMs(costMs);
        traceInfo.setTokenCount(modelResponse.tokenCount());
        LlmTraceContext.set(traceInfo);
        return content;
    }

    private ModelResponse requestWithSpringAi(
            AiModelConfig modelConfig,
            String systemPrompt,
            String userPrompt
    ) {
        OpenAiChatModel chatModel = resolveChatModel(modelConfig);
        ChatResponse response = chatModel.call(new Prompt(List.of(
                new SystemMessage(systemPrompt),
                new UserMessage(userPrompt)
        )));
        String content = response == null || response.getResult() == null
                ? null
                : response.getResult().getOutput().getText();
        return new ModelResponse(content, resolveTokenCount(response));
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
                .baseUrl(resolveApiBaseUrl(modelConfig.getBaseUrl()))
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

    /**
     * 规范化 OpenAI 兼容接口基础地址。
     *
     * @param baseUrl 数据库模型地址
     * @return Spring AI 使用的基础地址
     * @author 余炘洋
     * @date 2026-08-15
     */
    private String resolveApiBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new BizException(ErrorCode.CONFIG_INVALID, "模型基础地址不能为空");
        }
        String normalized = baseUrl.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (normalized.endsWith(OPENAI_CHAT_PATH)) {
            return normalized.substring(0, normalized.length() - "/chat/completions".length());
        }
        if (normalized.endsWith(OPENAI_API_PATH)) {
            return normalized;
        }
        return normalized + OPENAI_API_PATH;
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
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(secret.getBytes(StandardCharsets.UTF_8));
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
}
