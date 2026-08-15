package com.lingsql.llm;

/**
 * 模型调用接缝。
 *
 * <p>业务模块只依赖稳定的文本生成语义，模型选择、故障切换、供应商协议和使用量追踪由实现隐藏。</p>
 *
 * @author 余炘洋
 * @date 2026-08-15
 */
public interface IModelGateway {

    /**
     * 发送一次受控模型请求。
     *
     * @param systemPrompt 系统提示词
     * @param userPrompt 用户提示词
     * @return 模型输出
     * @author 余炘洋
     * @date 2026-08-15
     */
    String chat(String systemPrompt, String userPrompt);
}
