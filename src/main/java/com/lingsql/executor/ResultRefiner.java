package com.lingsql.executor;

import com.lingsql.llm.IModelGateway;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 查询结果润色器，将结构化数据转成自然语言回复。
 *
 * @author 余炘洋
 * @date 2026-01-22
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ResultRefiner {

    private final IModelGateway modelGateway;
    private static final String REFINE_FALLBACK_MESSAGE = "生成答复失败，请稍后重试。";
    private static final int MAX_REFINE_ROWS = 20;
    private static final int MAX_REFINE_COLUMNS = 20;
    private static final int MAX_REFINE_VALUE_LENGTH = 200;

    private static final String REFINER_PROMPT = """
        你是一个专业的数据分析助手，请基于问题与查询结果生成简洁、准确的回复。
        
        ### 约束要求
        1. 严禁捏造事实，只能根据提供的数据回答。
        2. 如果结果为空，请说明原因并提供 1 个可选澄清问题。
        3. 如果发生异常，请说明原因并提供 1 个可选澄清问题。
        4. 回复需通俗易懂，适合移动端阅读。
        """;

    /**
     * 将执行结果润色为自然语言。
     *
     * @param question 用户问题
     * @param result   执行结果
     * @return 业务回复
     * @author 余炘洋
     * @date 2026-01-22
     */
    public String refine(String question, SqlExecutionResult result) {
        return refineWithMetadata(question, result).answer();
    }

    /**
     * 将执行结果润色为自然语言，并返回调用状态用于编排轨迹。
     *
     * @param question 用户问题
     * @param result 执行结果
     * @return 分析结果
     * @author 余炘洋
     * @date 2026-07-29
     */
    public ResultRefinement refineWithMetadata(String question, SqlExecutionResult result) {
        long start = System.currentTimeMillis();
        if (result == null) {
            return new ResultRefinement("未获取到查询结果，请稍后重试。", false, System.currentTimeMillis() - start);
        }
        String dataStr = formatData(result.getData());
        String userPrompt = "用户问题: " + question + "\n" +
                "SQL: " + safe(result.getSql()) + "\n" +
                "执行状态: " + (result.isSuccess() ? "成功" : "失败") + "\n" +
                "错误原因: " + safe(result.getErrorMessage()) + "\n" +
                "查询结果: " + dataStr;
        try {
            return new ResultRefinement(modelGateway.chat(REFINER_PROMPT, userPrompt), true,
                    System.currentTimeMillis() - start);
        } catch (Exception ex) {
            log.warn("业务分析师生成答复失败，已使用降级答复", ex);
            return new ResultRefinement(REFINE_FALLBACK_MESSAGE, false, System.currentTimeMillis() - start);
        }
    }

    /**
     * 格式化查询结果数据。
     *
     * @param data 结果数据
     * @return 文本
     * @author 余炘洋
     * @date 2026-01-22
     */
    private String formatData(List<Map<String, Object>> data) {
        if (data == null || data.isEmpty()) {
            return "结果为空";
        }
        StringBuilder builder = new StringBuilder();
        int rowCount = Math.min(data.size(), MAX_REFINE_ROWS);
        for (int rowIndex = 0; rowIndex < rowCount; rowIndex++) {
            Map<String, Object> row = data.get(rowIndex);
            if (row == null || row.isEmpty()) {
                continue;
            }
            builder.append("第 ").append(rowIndex + 1).append(" 行：");
            int columnCount = 0;
            for (Map.Entry<String, Object> entry : row.entrySet()) {
                if (columnCount >= MAX_REFINE_COLUMNS) {
                    builder.append("字段已截断；");
                    break;
                }
                builder.append(entry.getKey()).append('=').append(abbreviateValue(entry.getValue())).append('；');
                columnCount++;
            }
            builder.append('\n');
        }
        if (data.size() > MAX_REFINE_ROWS) {
            builder.append("结果行数超过分析上限，已仅提供前 ").append(MAX_REFINE_ROWS).append(" 行。\n");
        }
        return builder.toString();
    }

    /**
     * 空值安全处理。
     *
     * @param value 原始值
     * @return 安全值
     * @author 余炘洋
     * @date 2026-01-22
     */
    private String safe(String value) {
        return value == null ? "" : value;
    }

    /**
     * 截断单元格值，避免将大字段无上限传给模型。
     *
     * @param value 原始值
     * @return 截断后的文本
     * @author 余炘洋
     * @date 2026-07-29
     */
    private String abbreviateValue(Object value) {
        String text = value == null ? "null" : String.valueOf(value);
        if (text.length() <= MAX_REFINE_VALUE_LENGTH) {
            return text;
        }
        return text.substring(0, MAX_REFINE_VALUE_LENGTH) + "...";
    }
}
