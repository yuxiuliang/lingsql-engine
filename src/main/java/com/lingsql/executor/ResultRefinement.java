package com.lingsql.executor;

/**
 * 业务分析师生成答复后的受控结果。
 *
 * @param answer 面向用户的答复
 * @param success 是否成功调用模型完成分析
 * @param durationMs 分析耗时
 * @param providerName 模型供应商
 * @param modelName 模型名称
 * @param modelVersion 模型版本
 * @param tokenCount Token 总量
 * @author 余炘洋
 * @date 2026-07-29
 */
public record ResultRefinement(
        String answer,
        boolean success,
        long durationMs,
        String providerName,
        String modelName,
        String modelVersion,
        Integer tokenCount
) {

    public ResultRefinement(String answer, boolean success, long durationMs) {
        this(answer, success, durationMs, null, null, null, null);
    }
}
