package com.lingsql.executor;

import com.lingsql.llm.LlmClient;
import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
public class ResultRefiner {

    private final LlmClient llmClient;
    private static final String REFINE_FALLBACK_MESSAGE = "生成答复失败，请稍后重试。";

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
        if (result == null) {
            return "未获取到查询结果，请稍后重试。";
        }
        String dataStr = formatData(result.getData());
        String userPrompt = "用户问题: " + question + "\n" +
                "SQL: " + safe(result.getSql()) + "\n" +
                "执行状态: " + (result.isSuccess() ? "成功" : "失败") + "\n" +
                "错误原因: " + safe(result.getErrorMessage()) + "\n" +
                "查询结果: " + dataStr;
        try {
            return llmClient.chat(REFINER_PROMPT, userPrompt);
        } catch (Exception ex) {
            return REFINE_FALLBACK_MESSAGE;
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
        return data.toString();
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
}
