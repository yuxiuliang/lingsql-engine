package com.lingsql.llm;

/**
 * 一次逻辑模型调用结果。
 *
 * @param content 模型输出
 * @param providerName 模型供应商
 * @param modelName 模型名称
 * @param modelVersion 模型版本
 * @param tokenCount Token 总量
 * @param costAmount 实际计费金额
 * @param invocationId 物理调用标识
 * @param billingVersion 计费策略版本
 * @param routeAttempt 物理路由序号
 * @author 余炘洋
 * @date 2026-08-15
 */
public record ModelCallResult(
        String content,
        String providerName,
        String modelName,
        String modelVersion,
        Integer tokenCount,
        Long costAmount,
        String invocationId,
        String billingVersion,
        int routeAttempt
) {

    /**
     * 替换文本输出并保留物理调用证据。
     *
     * @param normalizedContent 规范化文本
     * @return 新结果
     * @author 余炘洋
     * @date 2026-08-15
     */
    public ModelCallResult withContent(String normalizedContent) {
        return new ModelCallResult(
                normalizedContent,
                providerName,
                modelName,
                modelVersion,
                tokenCount,
                costAmount,
                invocationId,
                billingVersion,
                routeAttempt
        );
    }
}
