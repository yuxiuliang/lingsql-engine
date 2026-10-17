package com.lingsql.agent.model;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 人工审批决策请求。
 *
 * @author 余炘洋
 * @date 2026-10-17
 */
@Data
public class AgentApprovalDecisionCommand {

    /**
     * 是否通过。
     */
    @NotNull(message = "审批结论不能为空")
    private Boolean approved;

    /**
     * 审批意见。
     */
    @Size(max = 512, message = "审批意见不能超过512个字符")
    private String comment;
}
