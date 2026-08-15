package com.lingsql.llm;

import com.lingsql.agent.model.AiModelConfig;
import com.lingsql.common.exception.BizException;
import com.lingsql.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
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
    private static final double DEFAULT_TEMPERATURE = 0.0d;
    private static final int DEFAULT_MAX_TOKENS = 512;

    private final RestClient.Builder restClientBuilder;
    private final OpenAiEndpointResolver endpointResolver;
    private final OpenAiChatResponseParser responseParser;
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
                .uri(endpointResolver.resolveChatUrl(modelConfig.getBaseUrl()))
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(String.class);
        OpenAiChatResponse parsedResponse = responseParser.parse(response);
        return new ModelResponse(parsedResponse.content(), parsedResponse.totalTokens());
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

}
