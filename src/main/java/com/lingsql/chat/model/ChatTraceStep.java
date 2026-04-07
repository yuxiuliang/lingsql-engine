package com.lingsql.chat.model;

import lombok.AllArgsConstructor;
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
@AllArgsConstructor
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
}
