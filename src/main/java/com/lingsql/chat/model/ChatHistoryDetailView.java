package com.lingsql.chat.model;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 聊天历史详情视图对象。
 *
 * @author 余炘洋
 * @date 2026-01-23
 */
@Data
public class ChatHistoryDetailView {

    private Long id;
    private String question;
    private String answer;
    private String sql;
    private List<Map<String, Object>> data;
    private List<ChatTraceStep> trace;
    private LocalDateTime createTime;
}

