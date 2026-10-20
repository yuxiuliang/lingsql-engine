package com.lingsql.agent.memory.model;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 记忆候选拒绝请求，拒绝只更新候选状态，不生成任何生效记忆。
 *
 * @author 余炘洋
 * @date 2026-10-19
 */
@Data
public class AgentMemoryRejectRequest {

    /**
     * 拒绝原因，可为空。
     */
    @Size(max = 256, message = "拒绝原因长度不能超过 256 个字符")
    private String reason;
}
