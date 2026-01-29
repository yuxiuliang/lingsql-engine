package com.lingsql.audit.model;

import lombok.Data;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * SQL 执行日志写入请求。
 *
 * @author 余炘洋
 * @date 2026-01-26
 */
@Data
public class SqlExecuteLogCommand {

    /**
     * 请求ID。
     *
     * @author 余炘洋
     * @date 2026-01-29
     */
    private String requestId;

    /**
     * 租户ID。
     *
     * @author 余炘洋
     * @date 2026-01-26
     */
    private String tenantId;

    /**
     * 用户ID。
     *
     * @author 余炘洋
     * @date 2026-01-26
     */
    private Long userId;

    /**
     * 原始问题。
     *
     * @author 余炘洋
     * @date 2026-01-26
     */
    private String question;

    /**
     * AI 生成 SQL。
     *
     * @author 余炘洋
     * @date 2026-01-26
     */
    private String aiSql;

    /**
     * 执行 SQL。
     *
     * @author 余炘洋
     * @date 2026-01-26
     */
    private String executedSql;

    /**
     * 是否成功。
     *
     * @author 余炘洋
     * @date 2026-01-26
     */
    private boolean success;

    /**
     * 错误信息。
     *
     * @author 余炘洋
     * @date 2026-01-26
     */
    private String errorMessage;

    /**
     * 模型供应商。
     *
     * @author 余炘洋
     * @date 2026-01-26
     */
    private String modelProvider;

    /**
     * 模型名称。
     *
     * @author 余炘洋
     * @date 2026-01-26
     */
    private String modelName;

    /**
     * 模型版本。
     *
     * @author 余炘洋
     * @date 2026-01-26
     */
    private String modelVersion;

    /**
     * Token 数量。
     *
     * @author 余炘洋
     * @date 2026-01-27
     */
    private Integer tokenCount;

    /**
     * 按模型统计的 Token 数量。
     *
     * @author 余炘洋
     * @date 2026-01-29
     */
    private Map<String, Integer> modelTokenUsage;

    /**
     * 已预扣的模型 Token 数量。
     *
     * @author 余炘洋
     * @date 2026-01-29
     */
    private Map<String, Integer> reservedModelTokenUsage;

    /**
     * 成本金额。
     *
     * @author 余炘洋
     * @date 2026-01-27
     */
    private Long costAmount;

    /**
     * 已预扣成本金额。
     *
     * @author 余炘洋
     * @date 2026-01-29
     */
    private Long reservedCostAmount;

    /**
     * 账本事件序号。
     *
     * @author 余炘洋
     * @date 2026-01-29
     */
    private Integer billingEventSeq;

    /**
     * 计费版本。
     *
     * @author 余炘洋
     * @date 2026-01-27
     */
    private String billingVersion;

    /**
     * 执行耗时(毫秒)。
     *
     * @author 余炘洋
     * @date 2026-01-26
     */
    private Long executeCostMs;

    /**
     * 返回行数。
     *
     * @author 余炘洋
     * @date 2026-01-26
     */
    private Integer resultRows;

    /**
     * 请求来源。
     *
     * @author 余炘洋
     * @date 2026-01-26
     */
    private String requestSource;

    /**
     * 评分。
     *
     * @author 余炘洋
     * @date 2026-01-27
     */
    private Integer rating;

    /**
     * 评价内容。
     *
     * @author 余炘洋
     * @date 2026-01-27
     */
    private String comment;

    /**
     * 累加模型 Token 统计。
     *
     * @param modelName 模型名称
     * @param tokens    Token 数量
     * @author 余炘洋
     * @date 2026-01-29
     */
    public void addModelTokens(String modelName, Integer tokens) {
        if (tokens == null || tokens <= 0) {
            return;
        }
        String normalized = StringUtils.hasText(modelName) ? modelName.trim() : "UNKNOWN";
        if (modelTokenUsage == null) {
            modelTokenUsage = new HashMap<>();
        }
        Integer current = modelTokenUsage.get(normalized);
        if (current == null) {
            modelTokenUsage.put(normalized, tokens);
            return;
        }
        long total = (long) current + tokens;
        modelTokenUsage.put(normalized, total > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) total);
    }

    /**
     * 累加已预扣的模型 Token 统计。
     *
     * @param modelName 模型名称
     * @param tokens    Token 数量
     * @author 余炘洋
     * @date 2026-01-29
     */
    public void addReservedModelTokens(String modelName, Integer tokens) {
        if (tokens == null || tokens <= 0) {
            return;
        }
        String normalized = StringUtils.hasText(modelName) ? modelName.trim() : "UNKNOWN";
        if (reservedModelTokenUsage == null) {
            reservedModelTokenUsage = new HashMap<>();
        }
        Integer current = reservedModelTokenUsage.get(normalized);
        if (current == null) {
            reservedModelTokenUsage.put(normalized, tokens);
            return;
        }
        long total = (long) current + tokens;
        reservedModelTokenUsage.put(normalized, total > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) total);
    }

    /**
     * 获取下一次账本事件序号。
     *
     * @return 序号
     * @author 余炘洋
     * @date 2026-01-29
     */
    public int nextBillingEventSeq() {
        if (billingEventSeq == null || billingEventSeq <= 0) {
            billingEventSeq = 1;
            return billingEventSeq;
        }
        billingEventSeq = billingEventSeq + 1;
        return billingEventSeq;
    }
}
