package com.lingsql.ops.model;

import java.util.Locale;

/**
 * 告警通道投递结果。
 *
 * @param channel    通道编码
 * @param success    是否成功
 * @param statusCode HTTP 状态码
 * @param detail     明细说明
 * @author 余炘洋
 * @date 2026-04-15
 */
public record AlertDispatchChannelResult(String channel,
                                         boolean success,
                                         Integer statusCode,
                                         String detail) {

    /**
     * 构建成功结果。
     *
     * @param channel    通道编码
     * @param statusCode HTTP 状态码
     * @return 投递结果
     * @author 余炘洋
     * @date 2026-04-15
     */
    public static AlertDispatchChannelResult success(String channel, Integer statusCode) {
        return new AlertDispatchChannelResult(channel, true, statusCode, "投递成功");
    }

    /**
     * 构建失败结果。
     *
     * @param channel    通道编码
     * @param statusCode HTTP 状态码
     * @param detail     失败说明
     * @return 投递结果
     * @author 余炘洋
     * @date 2026-04-15
     */
    public static AlertDispatchChannelResult failure(String channel, Integer statusCode, String detail) {
        return new AlertDispatchChannelResult(channel, false, statusCode, detail);
    }

    /**
     * 生成便于日志展示的摘要。
     *
     * @return 摘要文本
     * @author 余炘洋
     * @date 2026-04-15
     */
    public String summarize() {
        StringBuilder builder = new StringBuilder();
        builder.append(resolveChannelName(channel))
                .append('=')
                .append(success ? "成功" : "失败");
        if (statusCode != null) {
            builder.append("(HTTP ").append(statusCode).append(')');
        }
        if (detail != null && !detail.isBlank() && !"投递成功".equals(detail)) {
            builder.append("：").append(detail.trim());
        }
        return builder.toString();
    }

    private String resolveChannelName(String value) {
        if (value == null || value.isBlank()) {
            return "未知通道";
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "webhook" -> "Webhook";
            case "dingtalk" -> "钉钉";
            case "wecom" -> "企微";
            default -> value.trim();
        };
    }
}
