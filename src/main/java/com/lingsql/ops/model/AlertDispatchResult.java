package com.lingsql.ops.model;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 告警投递结果。
 *
 * @param success        是否整体成功
 * @param summary        总体摘要
 * @param channelResults 通道明细
 * @author 余炘洋
 * @date 2026-04-15
 */
public record AlertDispatchResult(boolean success,
                                  String summary,
                                  List<AlertDispatchChannelResult> channelResults) {

    public AlertDispatchResult {
        channelResults = channelResults == null ? Collections.emptyList() : List.copyOf(channelResults);
    }

    /**
     * 构建未触发/预检查失败结果。
     *
     * @param summary 摘要
     * @return 投递结果
     * @author 余炘洋
     * @date 2026-04-15
     */
    public static AlertDispatchResult skipped(String summary) {
        return new AlertDispatchResult(false, summary, Collections.emptyList());
    }

    /**
     * 构建正常投递结果。
     *
     * @param success        是否成功
     * @param channelResults 通道结果
     * @return 投递结果
     * @author 余炘洋
     * @date 2026-04-15
     */
    public static AlertDispatchResult completed(boolean success, List<AlertDispatchChannelResult> channelResults) {
        return new AlertDispatchResult(success, null, channelResults);
    }

    /**
     * 生成用于运行日志展示的摘要。
     *
     * @return 摘要文本
     * @author 余炘洋
     * @date 2026-04-15
     */
    public String summarize() {
        if (summary != null && !summary.isBlank()) {
            return summary.trim();
        }
        if (channelResults.isEmpty()) {
            return "未产生命中通道";
        }
        return channelResults.stream()
                .map(AlertDispatchChannelResult::summarize)
                .collect(Collectors.joining("；"));
    }
}
