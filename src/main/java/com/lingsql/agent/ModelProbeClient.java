package com.lingsql.agent;

import com.lingsql.agent.model.AiModelConfig;
import com.lingsql.common.exception.BizException;
import com.lingsql.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 模型健康探测客户端，用于半开探活。
 *
 * @author 余炘洋
 * @date 2026-01-22
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ModelProbeClient {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(8);
    private static final String DEFAULT_CHAT_PATH = "/v1/chat/completions";
    private static final String DEFAULT_MODELS_PATH = "/v1/models";

    private final RestClient.Builder restClientBuilder;

    /**
     * 执行一次轻量探测。
     *
     * @param modelConfig 模型配置
     * @return 探测结果
     * @author 余炘洋
     * @date 2026-01-22
     */
    public ProbeResult probe(AiModelConfig modelConfig) {
        if (modelConfig == null) {
            return ProbeResult.fail("模型配置为空");
        }
        try {
            RestClient restClient = buildRestClient();
            HttpHeaders headers = buildHeaders(modelConfig);

            ProbeResult modelsProbe = probeModelsEndpoint(restClient, headers, modelConfig);
            if (modelsProbe.isSuccess()) {
                return modelsProbe;
            }

            log.debug("模型 /v1/models 探测失败，改用 chat 探测: id={}, provider={}, model={}, reason={}",
                    modelConfig.getId(),
                    modelConfig.getProviderName(),
                    modelConfig.getModelName(),
                    modelsProbe.getReason());

            return probeChatEndpoint(restClient, headers, modelConfig);
        } catch (Exception ex) {
            log.warn("模型探测失败: id={}, provider={}, model={}, reason={}",
                    modelConfig.getId(),
                    modelConfig.getProviderName(),
                    modelConfig.getModelName(),
                    ex.getMessage());
            return ProbeResult.fail(ex.getMessage());
        }
    }

    /**
     * 构建鉴权 Header。
     *
     * @param modelConfig 模型配置
     * @return Authorization 内容
     * @author 余炘洋
     * @date 2026-01-22
     */
    private String buildAuth(AiModelConfig modelConfig) {
        if (modelConfig.getApiKey() == null || modelConfig.getApiKey().isBlank()) {
            return "";
        }
        return "Bearer " + modelConfig.getApiKey();
    }

    /**
     * 构建 RestClient。
     *
     * @return RestClient
     * @author 余炘洋
     * @date 2026-01-22
     */
    private RestClient buildRestClient() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(DEFAULT_TIMEOUT);
        requestFactory.setReadTimeout(DEFAULT_TIMEOUT);
        return restClientBuilder
                .requestFactory(requestFactory)
                .build();
    }

    /**
     * 构建请求头。
     *
     * @param modelConfig 模型配置
     * @return 请求头
     * @author 余炘洋
     * @date 2026-01-22
     */
    private HttpHeaders buildHeaders(AiModelConfig modelConfig) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String auth = buildAuth(modelConfig);
        if (!auth.isBlank()) {
            headers.set("Authorization", auth);
        }
        return headers;
    }

    /**
     * 使用 /v1/models 探测。
     *
     * @param restClient RestClient
     * @param headers      请求头
     * @param modelConfig  模型配置
     * @return 探测结果
     * @author 余炘洋
     * @date 2026-01-22
     */
    private ProbeResult probeModelsEndpoint(RestClient restClient, HttpHeaders headers, AiModelConfig modelConfig) {
        try {
            String url = resolveModelsUrl(modelConfig.getBaseUrl());
            ResponseEntity<String> response = restClient.get()
                    .uri(url)
                    .headers(requestHeaders -> requestHeaders.addAll(headers))
                    .retrieve()
                    .toEntity(String.class);
            if (response.getStatusCode().is2xxSuccessful()) {
                return ProbeResult.success();
            }
            return ProbeResult.fail("models 状态码: " + response.getStatusCode());
        } catch (Exception ex) {
            return ProbeResult.fail(ex.getMessage());
        }
    }

    /**
     * 使用 /v1/chat/completions 探测。
     *
     * @param restClient RestClient
     * @param headers      请求头
     * @param modelConfig  模型配置
     * @return 探测结果
     * @author 余炘洋
     * @date 2026-01-22
     */
    private ProbeResult probeChatEndpoint(RestClient restClient, HttpHeaders headers, AiModelConfig modelConfig) {
        try {
            Map<String, Object> request = new HashMap<>();
            request.put("model", modelConfig.getModelName());
            request.put("temperature", 0.0d);
            request.put("max_tokens", 1);
            request.put("messages", List.of(
                    Map.of("role", "system", "content", "你是健康探测助手，仅回复 OK"),
                    Map.of("role", "user", "content", "health check")
            ));

            String url = resolveChatUrl(modelConfig.getBaseUrl());
            String response = restClient.post()
                    .uri(url)
                    .headers(requestHeaders -> requestHeaders.addAll(headers))
                    .body(request)
                    .retrieve()
                    .body(String.class);
            if (response == null || response.isBlank()) {
                return ProbeResult.fail("chat 响应为空");
            }
            return ProbeResult.success();
        } catch (Exception ex) {
            return ProbeResult.fail(ex.getMessage());
        }
    }

    /**
     * 解析聊天接口地址。
     *
     * @param baseUrl 模型基础地址
     * @return 完整请求地址
     * @author 余炘洋
     * @date 2026-01-22
     */
    private String resolveChatUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new BizException(ErrorCode.CONFIG_INVALID, "模型基础地址不能为空");
        }
        String trimmed = baseUrl.trim();
        if (trimmed.endsWith(DEFAULT_CHAT_PATH)) {
            return trimmed;
        }
        if (trimmed.endsWith("/")) {
            return trimmed.substring(0, trimmed.length() - 1) + DEFAULT_CHAT_PATH;
        }
        return trimmed + DEFAULT_CHAT_PATH;
    }

    /**
     * 解析模型列表接口地址。
     *
     * @param baseUrl 模型基础地址
     * @return 完整请求地址
     * @author 余炘洋
     * @date 2026-01-22
     */
    private String resolveModelsUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new BizException(ErrorCode.CONFIG_INVALID, "模型基础地址不能为空");
        }
        String trimmed = baseUrl.trim();
        if (trimmed.endsWith(DEFAULT_MODELS_PATH)) {
            return trimmed;
        }
        if (trimmed.endsWith(DEFAULT_CHAT_PATH)) {
            return trimmed.substring(0, trimmed.length() - DEFAULT_CHAT_PATH.length()) + DEFAULT_MODELS_PATH;
        }
        if (trimmed.endsWith("/")) {
            return trimmed.substring(0, trimmed.length() - 1) + DEFAULT_MODELS_PATH;
        }
        return trimmed + DEFAULT_MODELS_PATH;
    }

    /**
     * 探测结果对象。
     *
     * @author 余炘洋
     * @date 2026-01-22
     */
    public static class ProbeResult {
        private final boolean success;
        private final String reason;

        private ProbeResult(boolean success, String reason) {
            this.success = success;
            this.reason = reason;
        }

        public static ProbeResult success() {
            return new ProbeResult(true, "");
        }

        public static ProbeResult fail(String reason) {
            return new ProbeResult(false, reason == null ? "探测失败" : reason);
        }

        public boolean isSuccess() {
            return success;
        }

        public String getReason() {
            return reason;
        }
    }
}
