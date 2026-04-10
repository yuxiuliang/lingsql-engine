package com.lingsql.chat.model;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 问答接口响应实体。
 *
 * @author 余炘洋
 * @date 2026-01-22
 */
@Data
@AllArgsConstructor
public class ChatResult {

    /**
     * 自然语言答复。
     *
     * @author 余炘洋
     * @date 2026-01-22
     */
    private String answer;

    /**
     * 原始数据结果。
     *
     * @author 余炘洋
     * @date 2026-01-22
     */
    private List<Map<String, Object>> data;

    /**
     * 实际执行的 SQL。
     *
     * @author 余炘洋
     * @date 2026-01-22
     */
    private String sql;

    /**
     * 历史记录ID。
     *
     * @author 余炘洋
     * @date 2026-01-23
     */
    private Long historyId;

    /**
     * 执行轨迹。
     *
     * @author 余炘洋
     * @date 2026-04-07
     */
    private List<ChatTraceStep> trace;

    /**
     * 当前对话会话标识。
     *
     * @author 余炘洋
     * @date 2026-04-10
     */
    private String conversationId;
}
