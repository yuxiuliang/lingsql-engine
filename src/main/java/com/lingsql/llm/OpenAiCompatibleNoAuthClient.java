package com.lingsql.llm;

import com.lingsql.agent.model.AiModelConfig;
import com.lingsql.common.exception.BizException;
import com.lingsql.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * 无鉴权 OpenAI 兼容模型传输。
 *
 * <p>仅用于没有 API Key 的内网模型，保持升级前不发送 Authorization Header 的行为。</p>
 *
 * @author 余炘洋
 * @date 2026-08-15
 */
@Component
@RequiredArgsConstructor
class OpenAiCompatibleNoAuthClient {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(60);
    private static final String DEFAULT_CHAT_PATH = "/v1/chat/completions";
    private static final double DEFAULT_TEMPERATURE = 0.0d;
    private static final int DEFAULT_MAX_TOKENS = 512;

    private final ObjectMapper objectMapper;
    private final RestClient.Builder restClientBuilder;
    private volatile RestClient sharedRestClient;

    ModelResponse chat(AiModelConfig modelConfig, String systemPrompt, String userPrompt) {
        if (modelConfig == null || modelConfig.getModelName() == null || modelConfig.getModelName().isBlank()) {
            throw new BizException(ErrorCode.CONFIG_INVALID, "模型名称不能为空");
        }
        Map<String, Object> request = Map.of(
                "model", modelConfig.getModelName().trim(),
                "temperature", DEFAULT_TEMPERATURE,
                "max_tokens", DEFAULT_MAX_TOKENS,
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userPrompt)
                )
        );
        String response = resolveRestClient().post()
                .uri(resolveChatUrl(modelConfig.getBaseUrl()))
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(String.class);
        return parseResponse(response);
    }

    private RestClient resolveRestClient() {
        if (sharedRestClient != null) {
            return sharedRestClient;
        }
        synchronized (this) {
            if (sharedRestClient == null) {
                SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
                requestFactory.setConnectTimeout(DEFAULT_TIMEOUT);
                requestFactory.setReadTimeout(DEFAULT_TIMEOUT);
                sharedRestClient = restClientBuilder.requestFactory(requestFactory).build();
            }
            return sharedRestClient;
        }
    }

    private ModelResponse parseResponse(String response) {
        if (response == null || response.isBlank()) {
            return new ModelResponse("", null);
        }
        try {
            JsonNode root = objectMapper.readTree(response);
            JsonNode choices = root.path("choices");
            String content = choices.isArray() && !choices.isEmpty()
                    ? choices.get(0).path("message").path("content").stringValue("")
                    : root.path("content").stringValue("");
            long totalTokens = root.path("usage").path("total_tokens").asLong(-1);
            return new ModelResponse(content, totalTokens < 0 ? null : Math.toIntExact(totalTokens));
        } catch (Exception exception) {
            throw new BizException(ErrorCode.MODEL_UNAVAILABLE, "模型响应格式不兼容", exception);
        }
    }

    private String resolveChatUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new BizException(ErrorCode.CONFIG_INVALID, "模型基础地址不能为空");
        }
        String normalized = baseUrl.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (normalized.endsWith(DEFAULT_CHAT_PATH)) {
            return normalized;
        }
        if (normalized.endsWith("/v1")) {
            return normalized + "/chat/completions";
        }
        return normalized + DEFAULT_CHAT_PATH;
    }
}
