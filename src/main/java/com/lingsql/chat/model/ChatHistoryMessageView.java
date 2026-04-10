package com.lingsql.chat.model;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 历史会话消息视图对象。
 *
 * @author 余炘洋
 * @date 2026-04-10
 */
@Data
public class ChatHistoryMessageView {

    private String role;
    private String content;
    private String sql;
    private List<Map<String, Object>> data;
    private List<ChatTraceStep> trace;
    private LocalDateTime createTime;
}
