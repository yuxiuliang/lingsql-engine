package com.lingsql.chat.model;

import lombok.Data;

/**
 * 聊天记忆消息模型。
 *
 * @author 余炘洋
 * @date 2026-01-26
 */
@Data
public class ChatMemoryMessage {

    /**
     * 角色标识(User/AI)。
     *
     * @author 余炘洋
     * @date 2026-01-26
     */
    private String role;

    /**
     * 对话内容。
     *
     * @author 余炘洋
     * @date 2026-01-26
     */
    private String content;

    /**
     * 成功执行的 SQL 文本。
     *
     * @author 余炘洋
     * @date 2026-01-26
     */
    private String sql;

    /**
     * 最近一次结果主键 ID。
     *
     * @author 余炘洋
     * @date 2026-01-26
     */
    private String lastSeenId;

    /**
     * 构建用户消息。
     *
     * @param content 用户内容
     * @return 消息对象
     * @author 余炘洋
     * @date 2026-01-26
     */
    public static ChatMemoryMessage user(String content) {
        ChatMemoryMessage message = new ChatMemoryMessage();
        message.setRole("User");
        message.setContent(content);
        return message;
    }

    /**
     * 构建 AI 消息。
     *
     * @param content    AI 内容
     * @param sql        SQL 文本
     * @param lastSeenId 最近一次主键 ID
     * @return 消息对象
     * @author 余炘洋
     * @date 2026-01-26
     */
    public static ChatMemoryMessage assistant(String content, String sql, String lastSeenId) {
        ChatMemoryMessage message = new ChatMemoryMessage();
        message.setRole("AI");
        message.setContent(content);
        message.setSql(sql);
        message.setLastSeenId(lastSeenId);
        return message;
    }
}
