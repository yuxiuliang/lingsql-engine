package com.lingsql.chat.model;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 问答执行轨迹步骤。
 *
 * @author 余炘洋
 * @date 2026-04-07
 */
@Data
@NoArgsConstructor
public class ChatTraceStep {

    /**
     * 步骤类型：THOUGHT、ACTION、OBSERVATION、FINAL。
     *
     * @author 余炘洋
     * @date 2026-04-07
     */
    private String type;

    /**
     * 步骤标题。
     *
     * @author 余炘洋
     * @date 2026-04-07
     */
    private String title;

    /**
     * 步骤说明。
     *
     * @author 余炘洋
     * @date 2026-04-07
     */
    private String content;

    /**
     * 步骤状态：SUCCESS、FAILURE、INFO。
     *
     * @author 余炘洋
     * @date 2026-04-07
     */
    private String status;

    /**
     * 所属尝试轮次，首轮为 1，纠错轮次为 2。
     *
     * @author 余炘洋
     * @date 2026-04-07
     */
    private Integer attempt;

    /**
     * 负责该步骤的编排角色。
     *
     * @author 余炘洋
     * @date 2026-07-29
     */
    private String role;

    /**
     * 本步骤的处理耗时（毫秒）。
     *
     * @author 余炘洋
     * @date 2026-07-29
     */
    private Long durationMs;

    /**
     * 构造兼容历史轨迹的步骤。
     *
     * @param type 步骤类型
     * @param title 步骤标题
     * @param content 步骤说明
     * @param status 步骤状态
     * @param attempt 所属轮次
     * @author 余炘洋
     * @date 2026-07-29
     */
    public ChatTraceStep(String type, String title, String content, String status, Integer attempt) {
        this(type, title, content, status, attempt, null, null);
    }

    /**
     * 构造包含角色信息的步骤。
     *
     * @param type 步骤类型
     * @param title 步骤标题
     * @param content 步骤说明
     * @param status 步骤状态
     * @param attempt 所属轮次
     * @param role 编排角色
     * @param durationMs 处理耗时
     * @author 余炘洋
     * @date 2026-07-29
     */
    public ChatTraceStep(String type,
                         String title,
                         String content,
                         String status,
                         Integer attempt,
                         String role,
                         Long durationMs) {
        this.type = type;
        this.title = title;
        this.content = content;
        this.status = status;
        this.attempt = attempt;
        this.role = role;
        this.durationMs = durationMs;
    }
}
