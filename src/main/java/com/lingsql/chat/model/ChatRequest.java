package com.lingsql.chat.model;

import com.lingsql.common.validation.ChatQuestionLength;
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
    @ChatQuestionLength
    private String question;
}
