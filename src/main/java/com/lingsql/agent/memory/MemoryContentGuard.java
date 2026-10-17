package com.lingsql.agent.memory;

import com.lingsql.common.exception.BizException;
import com.lingsql.common.exception.ErrorCode;
import com.lingsql.config.LingSqlProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 记忆内容治理校验：长期记忆会进入系统提示词，必须在写入前完成规范化、注入与敏感信息过滤。
 *
 * @author 余炘洋
 * @date 2026-10-18
 */
@Component
@RequiredArgsConstructor
public class MemoryContentGuard {

    /**
     * 记忆正文最大长度，与数据库列宽保持一致。
     */
    public static final int MAX_FACT_LENGTH = 500;

    /**
     * 明显敏感信息模式：凭据类关键词、证件与银行卡号、手机号。
     */
    private static final List<Pattern> SENSITIVE_PATTERNS = List.of(
            Pattern.compile("(?i)\\b(password|passwd|pwd|secret|token|credential|api[-_]?key|access[-_]?key)\\b"),
            Pattern.compile("密码|密钥|凭据|访问令牌|身份证|银行卡|信用卡"),
            Pattern.compile("\\b1[3-9]\\d{9}\\b"),
            Pattern.compile("\\b\\d{17}[0-9Xx]\\b")
    );

    private final LingSqlProperties properties;

    /**
     * 校验并规范化记忆正文。
     *
     * @param factText 原始记忆正文
     * @return 规范化后的记忆正文
     * @author 余炘洋
     * @date 2026-10-18
     */
    public String validateAndNormalize(String factText) {
        if (!StringUtils.hasText(factText)) {
            throw new BizException(ErrorCode.INVALID_PARAM, "记忆内容不能为空");
        }
        String normalized = factText.trim().replaceAll("\\s+", " ");
        if (normalized.length() > MAX_FACT_LENGTH) {
            throw new BizException(ErrorCode.INVALID_PARAM, "记忆内容长度不能超过 " + MAX_FACT_LENGTH + " 个字符");
        }
        validateInjection(normalized);
        validateSensitive(normalized);
        return normalized;
    }

    /**
     * 注入关键词检测：记忆属于高信任上下文，命中即拒绝写入。
     *
     * @param factText 记忆正文
     * @author 余炘洋
     * @date 2026-10-18
     */
    private void validateInjection(String factText) {
        List<String> keywords = properties.getSecurity().getPromptInjectionKeywords();
        if (keywords == null || keywords.isEmpty()) {
            return;
        }
        String lowerText = factText.toLowerCase();
        for (String keyword : keywords) {
            if (StringUtils.hasText(keyword) && lowerText.contains(keyword.toLowerCase())) {
                throw new BizException(ErrorCode.SECURITY_VIOLATION, "记忆内容包含疑似 Prompt 注入指令，已拒绝写入");
            }
        }
    }

    /**
     * 敏感信息检测：敏感信息默认不得进入模型记忆。
     *
     * @param factText 记忆正文
     * @author 余炘洋
     * @date 2026-10-18
     */
    private void validateSensitive(String factText) {
        for (Pattern pattern : SENSITIVE_PATTERNS) {
            if (pattern.matcher(factText).find()) {
                throw new BizException(ErrorCode.SECURITY_VIOLATION, "记忆内容包含敏感信息，已拒绝写入");
            }
        }
    }
}
