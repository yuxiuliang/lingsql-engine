package com.lingsql.llm;

import lombok.Data;

/**
 * 模型调用追踪信息。
 *
 * @author 余炘洋
 * @date 2026-01-26
 */
@Data
public class LlmTraceInfo {

    /**
     * 模型供应商。
     *
     * @author 余炘洋
     * @date 2026-01-26
     */
    private String providerName;

    /**
     * 模型名称。
     *
     * @author 余炘洋
     * @date 2026-01-26
     */
    private String modelName;

    /**
     * 模型版本。
     *
     * @author 余炘洋
     * @date 2026-01-26
     */
    private String modelVersion;

    /**
     * 调用耗时(毫秒)。
     *
     * @author 余炘洋
     * @date 2026-01-26
     */
    private Long costMs;
}
