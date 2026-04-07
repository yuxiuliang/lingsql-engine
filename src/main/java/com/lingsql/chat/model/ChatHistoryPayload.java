package com.lingsql.chat.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 聊天历史扩展载荷，兼容结果数据与执行轨迹。
 *
 * @author 余炘洋
 * @date 2026-04-07
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatHistoryPayload {

    /**
     * 查询结果数据。
     *
     * @author 余炘洋
     * @date 2026-04-07
     */
    private List<Map<String, Object>> data;

    /**
     * 可视化执行轨迹。
     *
     * @author 余炘洋
     * @date 2026-04-07
     */
    private List<ChatTraceStep> trace;
}
