package com.lingsql.web.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 聊天运行时配置返回体。
 *
 * @author 余炘洋
 * @date 2026-01-23
 */
@Data
@AllArgsConstructor
public class ChatRuntimeConfig {

    /**
     * 用户问题最大长度。
     *
     * @author 余炘洋
     * @date 2026-01-23
     */
    private int maxQuestionLength;
}
