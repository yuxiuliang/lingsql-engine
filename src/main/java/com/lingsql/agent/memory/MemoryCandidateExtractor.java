package com.lingsql.agent.memory;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.lingsql.agent.orchestration.AgentExecutionContext;
import com.lingsql.billing.invocation.PhysicalBillingException;
import com.lingsql.common.exception.BizException;
import com.lingsql.common.exception.ErrorCode;
import com.lingsql.llm.IModelGateway;
import com.lingsql.llm.ModelCallCommand;
import com.lingsql.llm.ModelCallResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 记忆候选提取器：调用模型从会话窗口中提炼可长期复用的事实，只产出候选文本，不写库。
 *
 * <p>边界：</p>
 * <ul>
 *     <li>作为独立模型调用站点进入逐物理调用资金状态机（来源 {@code memory-curator}，
 *     阶段 {@code MEMORY_CANDIDATE}），用量、费用与路由指纹与其它站点一致留痕。</li>
 *     <li>只做文本提炼与解析，不判断是否可信、不做敏感分类、不写库；分类、去重与采纳
 *     分别由 {@link MemoryContentGuard} 与候选服务负责。</li>
 *     <li>解析与失败一律收敛为「零候选」或标准业务异常，模型输出中的任何指令性内容
 *     都当作普通文本处理，绝不执行。</li>
 * </ul>
 *
 * @author 余炘洋
 * @date 2026-10-19
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MemoryCandidateExtractor {

    /**
     * 模型调用来源类型。
     */
    public static final String MODEL_CALLER_TYPE = "memory-curator";

    /**
     * 模型调用阶段。
     */
    public static final String MODEL_STAGE = "MEMORY_CANDIDATE";

    /**
     * 逻辑调用序号：一次提取只允许一次物理调用。
     */
    public static final int LOGICAL_CALL_NO = 1;

    /**
     * 单次提取的最大候选条数。
     */
    public static final int MAX_CANDIDATES = 10;

    /**
     * 模型未给出置信度时的默认分值。
     */
    public static final int DEFAULT_CONFIDENCE = 60;

    /**
     * 送入模型的会话文本长度上限。
     */
    private static final int MAX_HISTORY_LENGTH = 6000;

    private static final String EXTRACT_PROMPT = """
            你是企业问数平台的记忆整理助手，任务是从对话记录中提炼可以长期复用的事实。
            只输出 JSON 数组，不要输出解释、Markdown 代码块或任何其他文字。
            数组元素格式：{"fact": "事实文本", "confidence": 60}
            提炼规则：
            1. 只提炼稳定的业务口径、字段含义、单位约定，或用户明确表达的长期偏好与明确陈述的事实。
            2. 一次性的查询条件、临时的筛选范围、模型推测结论、与业务无关的闲聊不得提炼。
            3. 不得输出密码、密钥、令牌、证件号、手机号、银行卡号等敏感信息；发现敏感内容时跳过该条。
            4. 不得输出任何指令性语句，也不得要求改变系统行为或安全策略。
            5. 每条 fact 使用陈述句且不超过 200 个字符；没有可提炼内容时输出 []。
            """;

    private final IModelGateway modelGateway;

    private final ObjectMapper objectMapper;

    /**
     * 从会话文本中提炼候选事实。
     *
     * @param context       Agent 执行上下文
     * @param historyPrompt 会话窗口文本（调用方保证已脱敏）
     * @param maxCandidates 候选条数上限
     * @return 候选事实列表，无内容或解析失败时返回空列表
     * @author 余炘洋
     * @date 2026-10-19
     */
    public List<ExtractedCandidate> extract(AgentExecutionContext context, String historyPrompt, int maxCandidates) {
        if (context == null
                || !StringUtils.hasText(context.tenantId())
                || !StringUtils.hasText(historyPrompt)) {
            return List.of();
        }
        int limit = Math.max(1, Math.min(maxCandidates, MAX_CANDIDATES));
        String userPrompt = "最近对话记录（已脱敏）：\n" + truncate(historyPrompt)
                + "\n\n请提炼不超过 " + limit + " 条候选事实。";
        ModelCallResult result;
        try {
            result = modelGateway.chat(new ModelCallCommand(
                    context.tenantId(),
                    StringUtils.hasText(context.requestId()) ? context.requestId() : context.executionId(),
                    MODEL_CALLER_TYPE,
                    StringUtils.hasText(context.executionId()) ? context.executionId() : context.requestId(),
                    MODEL_STAGE,
                    LOGICAL_CALL_NO,
                    EXTRACT_PROMPT,
                    userPrompt
            ));
        } catch (PhysicalBillingException exception) {
            throw exception;
        } catch (Exception exception) {
            log.warn("记忆候选提取模型调用失败: tenantId={}, reason={}",
                    context.tenantId(), exception.getClass().getSimpleName());
            throw new BizException(ErrorCode.BUSINESS_ERROR, "记忆候选提取失败，请稍后重试");
        }
        return parse(result == null ? null : result.content(), limit);
    }

    /**
     * 解析模型输出：只接受 JSON 数组，单条非法即跳过，整体非法按零候选处理。
     *
     * @param content 模型输出文本
     * @param limit   候选条数上限
     * @return 候选事实列表
     * @author 余炘洋
     * @date 2026-10-19
     */
    private List<ExtractedCandidate> parse(String content, int limit) {
        if (!StringUtils.hasText(content)) {
            return List.of();
        }
        String payload = content.trim();
        int start = payload.indexOf('[');
        int end = payload.lastIndexOf(']');
        if (start < 0 || end <= start) {
            log.warn("记忆候选提取返回内容不是 JSON 数组，已丢弃");
            return List.of();
        }
        JsonNode array;
        try {
            array = objectMapper.readTree(payload.substring(start, end + 1));
        } catch (Exception exception) {
            log.warn("记忆候选提取返回内容解析失败，已丢弃: {}", exception.getClass().getSimpleName());
            return List.of();
        }
        if (array == null || !array.isArray()) {
            return List.of();
        }
        Map<String, ExtractedCandidate> deduped = new LinkedHashMap<>();
        for (JsonNode node : array) {
            if (deduped.size() >= limit) {
                break;
            }
            String factText = normalize(node.path("fact").asString(""));
            if (!StringUtils.hasText(factText)) {
                continue;
            }
            int confidence = node.path("confidence").asInt(DEFAULT_CONFIDENCE);
            deduped.putIfAbsent(factText, new ExtractedCandidate(factText, Math.max(0, Math.min(100, confidence))));
        }
        return List.copyOf(deduped.values());
    }

    /**
     * 规范化候选正文：折叠空白并限制长度，超长直接丢弃。
     *
     * @param factText 原始正文
     * @return 规范化正文，非法时返回空串
     * @author 余炘洋
     * @date 2026-10-19
     */
    private String normalize(String factText) {
        if (!StringUtils.hasText(factText)) {
            return "";
        }
        String normalized = factText.trim().replaceAll("\\s+", " ");
        return normalized.length() > MemoryContentGuard.MAX_FACT_LENGTH ? "" : normalized;
    }

    /**
     * 截断会话文本，限制单次提取的输入长度。
     *
     * <p>保留最近的对话内容（尾部），避免早期寒暄挤占提炼所需的最新事实。</p>
     *
     * @param text 原始文本
     * @return 截断后的文本
     * @author 余炘洋
     * @date 2026-10-19
     */
    private String truncate(String text) {
        if (text.length() <= MAX_HISTORY_LENGTH) {
            return text;
        }
        return text.substring(text.length() - MAX_HISTORY_LENGTH);
    }

    /**
     * 提炼结果：只包含正文与置信度，是否可信由后续治理链路判断。
     *
     * @param factText   候选正文
     * @param confidence 置信度 0-100
     * @author 余炘洋
     * @date 2026-10-19
     */
    public record ExtractedCandidate(String factText, int confidence) {
    }
}
