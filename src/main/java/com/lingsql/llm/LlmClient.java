package com.lingsql.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingsql.agent.ModelHealthService;
import com.lingsql.agent.ModelRouter;
import com.lingsql.agent.model.AiModelConfig;
import com.lingsql.common.exception.BizException;
import com.lingsql.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 私有部署大模型客户端，采用 OpenAI 兼容格式调用。
 *
 * @author 余炘洋
 * @date 2026-01-22
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LlmClient {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(60);
    private static final String DEFAULT_CHAT_PATH = "/v1/chat/completions";
    private static final double DEFAULT_TEMPERATURE = 0.0d;
    private static final int DEFAULT_MAX_TOKENS = 512;

    private final ModelRouter modelRouter;
    private final ModelHealthService healthService;
    private final ObjectMapper objectMapper;
    private final RestTemplateBuilder restTemplateBuilder;
    private volatile RestTemplate sharedRestTemplate;

    /**
     * 发送聊天请求。
     *
     * @param systemPrompt 系统提示词
     * @param userPrompt   用户输入
     * @return 模型输出
     * @author 余炘洋
     * @date 2026-01-22
     */
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
        Map<String, Object> request = new HashMap<>();
        request.put("model", modelConfig.getModelName());
        request.put("temperature", DEFAULT_TEMPERATURE);
        request.put("max_tokens", DEFAULT_MAX_TOKENS);
        request.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userPrompt)
        ));

        RestTemplate restTemplate = resolveRestTemplate();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String auth = buildAuth(modelConfig);
        if (!auth.isBlank()) {
            headers.set("Authorization", auth);
        }

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);
        String url = resolveChatUrl(modelConfig.getBaseUrl());
        String response = restTemplate.postForObject(url, entity, String.class);
        ParsedLlmResponse parsedResponse = parseResponse(response);
        String content = parsedResponse.content();
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
        traceInfo.setTokenCount(parsedResponse.tokenCount());
        LlmTraceContext.set(traceInfo);
        return content;
    }

    /**
     * 获取共享 RestTemplate，避免每次请求重复构建客户端对象。
     *
     * @return RestTemplate
     * @author 余炘洋
     * @date 2026-02-28
     */
    private RestTemplate resolveRestTemplate() {
        if (sharedRestTemplate != null) {
            return sharedRestTemplate;
        }
        synchronized (this) {
            if (sharedRestTemplate != null) {
                return sharedRestTemplate;
            }
            sharedRestTemplate = restTemplateBuilder
                    .requestFactory(() -> {
                        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
                        factory.setConnectTimeout((int) DEFAULT_TIMEOUT.toMillis());
                        factory.setReadTimeout((int) DEFAULT_TIMEOUT.toMillis());
                        return factory;
                    })
                    .build();
            return sharedRestTemplate;
        }
    }

    /**
     * 解析模型响应内容与 Token 信息。
     *
     * @param response 原始响应
     * @return 解析结果
     * @author 余炘洋
     * @date 2026-01-27
     */
    private ParsedLlmResponse parseResponse(String response) {
        if (response == null || response.isBlank()) {
            return new ParsedLlmResponse("", null);
        }
        try {
            JsonNode root = objectMapper.readTree(response);
            String content = "";
            JsonNode choices = root.path("choices");
            if (choices.isArray() && !choices.isEmpty()) {
                content = choices.get(0).path("message").path("content").asText("");
            } else {
                content = root.path("content").asText("");
            }
            Integer tokenCount = null;
            JsonNode usage = root.path("usage");
            if (usage.isObject()) {
                long totalTokens = usage.path("total_tokens").asLong(-1);
                if (totalTokens >= 0) {
                    tokenCount = (int) totalTokens;
                }
            }
            return new ParsedLlmResponse(content, tokenCount);
        } catch (Exception e) {
            log.error("解析 LLM 返回失败: {}", response, e);
            return new ParsedLlmResponse(response, null);
        }
    }

    /**
     * 模型响应解析结果。
     *
     * @param content    模型输出
     * @param tokenCount Token 数量
     * @author 余炘洋
     * @date 2026-01-27
     */
    private record ParsedLlmResponse(String content, Integer tokenCount) {
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
}
