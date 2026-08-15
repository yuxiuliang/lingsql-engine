package com.lingsql.llm;

import com.lingsql.common.exception.BizException;
import com.lingsql.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * OpenAI 兼容聊天响应解析器。
 *
 * <p>只有标准的 choices[0].message.content 才能证明模型真实完成了一次聊天调用，HTTP 200、HTML
 * 页面、错误 JSON 或供应商私有响应均不能作为健康证据。</p>
 *
 * @author 余炘洋
 * @date 2026-08-15
 */
@Component
@RequiredArgsConstructor
public class OpenAiChatResponseParser {

    private final ObjectMapper objectMapper;

    /**
     * 解析并校验聊天响应。
     *
     * @param response 原始响应
     * @return 标准聊天响应
     * @author 余炘洋
     * @date 2026-08-15
     */
    public OpenAiChatResponse parse(String response) {
        if (!StringUtils.hasText(response)) {
            throw incompatibleResponse(null);
        }
        try {
            JsonNode root = objectMapper.readTree(response);
            JsonNode error = root.path("error");
            if (!error.isMissingNode() && !error.isNull()) {
                throw incompatibleResponse(null);
            }
            JsonNode choices = root.path("choices");
            if (!choices.isArray() || choices.isEmpty()) {
                throw incompatibleResponse(null);
            }
            String content = choices.get(0)
                    .path("message")
                    .path("content")
                    .stringValue(null);
            if (!StringUtils.hasText(content)) {
                throw incompatibleResponse(null);
            }
            long totalTokens = root.path("usage").path("total_tokens").asLong(-1);
            Integer normalizedTokens = totalTokens < 0 || totalTokens > Integer.MAX_VALUE
                    ? null
                    : (int) totalTokens;
            return new OpenAiChatResponse(content, normalizedTokens);
        } catch (BizException exception) {
            throw exception;
        } catch (Exception exception) {
            throw incompatibleResponse(exception);
        }
    }

    private BizException incompatibleResponse(Throwable cause) {
        return new BizException(ErrorCode.MODEL_UNAVAILABLE, "模型响应不符合 OpenAI 兼容协议", cause);
    }
}
