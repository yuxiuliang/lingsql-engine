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
}
