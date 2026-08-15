package com.lingsql.llm;

/**
 * 模型调用接缝。
 *
 * <p>业务模块只依赖稳定的结构化调用语义，模型选择、物理路由、计费和供应商协议由实现隐藏。</p>
 *
 * @author 余炘洋
 * @date 2026-08-15
 */
public interface IModelGateway {

    /**
     * 发送一次受控模型请求。
     *
     * @param command 显式模型调用命令
     * @return 模型调用结果
     * @author 余炘洋
     * @date 2026-08-15
     */
    ModelCallResult chat(ModelCallCommand command);
}
