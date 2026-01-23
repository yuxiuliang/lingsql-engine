package com.lingsql.web.dto;

import lombok.Data;

/**
 * 聊天历史更新请求体。
 *
 * @author 余炘洋
 * @date 2026-01-23
 */
@Data
public class ChatHistoryUpdateRequest {

    /**
     * 历史问题标题。
     *
     * @author 余炘洋
     * @date 2026-01-23
     */
    private String question;
}

