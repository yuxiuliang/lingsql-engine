package com.lingsql.llm;

/**
 * 模型调用的标准化响应。
 *
 * @param content 模型输出
 * @param tokenCount Token 总量
 * @author 余炘洋
 * @date 2026-08-15
 */
record ModelResponse(String content, Integer tokenCount) {
}
