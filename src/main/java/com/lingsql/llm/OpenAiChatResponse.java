package com.lingsql.llm;

/**
 * OpenAI 兼容聊天响应中的可用业务字段。
 *
 * @param content 助手回复
 * @param totalTokens 总 Token 数，供应商未返回时为空
 * @author 余炘洋
 * @date 2026-08-15
 */
public record OpenAiChatResponse(String content, Integer totalTokens) {
}
