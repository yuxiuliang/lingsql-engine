package com.lingsql.schema;

import com.lingsql.schema.entity.JoinRule;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 语义映射与 Join 规则提供器。
 *
 * @author 余炘洋
 * @date 2026-01-22
 */
@Component
@RequiredArgsConstructor
public class SemanticLexicon {

    private final SemanticConfigService semanticConfigService;

    /**
     * 输出业务术语字典。
     *
     * @param tenantId 租户标识
     * @return 术语映射文本
     * @author 余炘洋
     * @date 2026-01-22
     */
    public String renderLexicon(String tenantId) {
        Map<String, String> lexicon = semanticConfigService.getLexiconMap(tenantId);
        if (lexicon.isEmpty()) {
            return "无自定义业务术语字典。";
        }
        return lexicon.entrySet().stream()
                .map(entry -> entry.getKey() + " => " + entry.getValue())
                .collect(Collectors.joining("\n"));
    }

    /**
     * 输出 Join 规则列表。
     *
     * @param tenantId 租户标识
     * @return Join 规则文本
     * @author 余炘洋
     * @date 2026-01-22
     */
    public String renderJoinRules(String tenantId) {
        List<JoinRule> joinRules = semanticConfigService.getJoinRules(tenantId);
        if (joinRules.isEmpty()) {
            return "无预定义 Join 规则。";
        }
        return joinRules.stream()
                .map(rule -> String.format("%s JOIN %s ON %s (%s)",
                        rule.getLeftTable(), rule.getRightTable(), rule.getOnExpr(), rule.getDescription()))
                .collect(Collectors.joining("\n"));
    }

    /**
     * 获取语义字典映射。
     *
     * @param tenantId 租户标识
     * @return 字典映射
     * @author 余炘洋
     * @date 2026-01-22
     */
    public Map<String, String> getLexiconMap(String tenantId) {
        return semanticConfigService.getLexiconMap(tenantId);
    }
}
