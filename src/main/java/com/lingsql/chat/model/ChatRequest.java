package com.lingsql.chat.model;

import com.lingsql.common.validation.ChatQuestionLength;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 请求体 DTO。
 *
 * @author 余炘洋
 * @date 2026-01-22
 */
@Data
public class ChatRequest {
    /**
     * 用户问题。
     *
     * @author 余炘洋
     * @date 2026-01-22
     */
    @NotBlank(message = "问题不能为空")
    @ChatQuestionLength
    private String question;

    /**
     * 前端当前对话会话标识。
     *
     * @author 余炘洋
     * @date 2026-04-10
     */
    private String conversationId;

    /**
     * 是否要求人工审批后再执行。
     *
     * <p>为 {@code true} 时本次提问只创建人工审批节点，审批通过前不调用模型、不执行 SQL。</p>
     *
     * @author 余炘洋
     * @date 2026-10-17
     */
    private Boolean approvalRequired;
}
