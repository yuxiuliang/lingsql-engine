package com.lingsql.llm;

import com.lingsql.common.exception.BizException;
import com.lingsql.common.exception.ErrorCode;
import org.springframework.stereotype.Component;

/**
 * OpenAI 兼容接口地址解析器。
 *
 * <p>模型探测、Spring AI 和无鉴权调用必须共享同一套地址语义，避免验证地址与真实流量地址不一致。</p>
 *
 * @author 余炘洋
 * @date 2026-08-15
 */
@Component
public class OpenAiEndpointResolver {

    private static final String API_PATH = "/v1";
    private static final String CHAT_PATH = "/v1/chat/completions";
    private static final String CHAT_RESOURCE_PATH = "/chat/completions";

    /**
     * 解析聊天补全接口地址。
     *
     * @param baseUrl 模型基础地址、v1 地址或完整聊天接口地址
     * @return 完整聊天补全接口地址
     * @author 余炘洋
     * @date 2026-08-15
     */
    public String resolveChatUrl(String baseUrl) {
        String normalized = normalize(baseUrl);
        if (normalized.endsWith(CHAT_PATH)) {
            return normalized;
        }
        if (normalized.endsWith(API_PATH)) {
            return normalized + CHAT_RESOURCE_PATH;
        }
        return normalized + CHAT_PATH;
    }

    /**
     * 解析 Spring AI 使用的 API 基础地址。
     *
     * @param baseUrl 模型基础地址、v1 地址或完整聊天接口地址
     * @return 以 v1 结尾的 API 基础地址
     * @author 余炘洋
     * @date 2026-08-15
     */
    public String resolveApiBaseUrl(String baseUrl) {
        String normalized = normalize(baseUrl);
        if (normalized.endsWith(CHAT_PATH)) {
            return normalized.substring(0, normalized.length() - CHAT_RESOURCE_PATH.length());
        }
        if (normalized.endsWith(API_PATH)) {
            return normalized;
        }
        return normalized + API_PATH;
    }

    private String normalize(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new BizException(ErrorCode.CONFIG_INVALID, "模型基础地址不能为空");
        }
        String normalized = baseUrl.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (normalized.isBlank()) {
            throw new BizException(ErrorCode.CONFIG_INVALID, "模型基础地址不能为空");
        }
        return normalized;
    }
}
