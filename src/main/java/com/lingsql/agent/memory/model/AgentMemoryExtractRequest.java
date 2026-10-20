package com.lingsql.agent.memory.model;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 记忆候选提取请求：只从当前会话窗口提炼候选，候选默认不生效。
 *
 * @author 余炘洋
 * @date 2026-10-19
 */
@Data
public class AgentMemoryExtractRequest {

    /**
     * 候选来源标识，为空时使用当前会话标识。
     */
    @Size(max = 128, message = "候选来源标识长度不能超过 128 个字符")
    private String sourceRef;

    /**
     * 候选条数上限，为空使用服务端默认值。
     */
    @Min(value = 1, message = "候选条数上限不能小于 1")
    @Max(value = 10, message = "候选条数上限不能大于 10")
    private Integer maxCandidates;
}
