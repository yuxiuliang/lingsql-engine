package com.lingsql.agent.knowledge;

import com.lingsql.agent.plugin.KnowledgeRetrievePlugin;
import org.springframework.util.StringUtils;

/**
 * 知识参考数据渲染器：把检索命中统一渲染成可追溯的参考文本。
 *
 * <p>渲染规则固定为「序号 + 标题 + 版本 + 来源 + 文档标识 + 正文片段」，
 * 保证任何进入提示词或工具结果的知识都能回溯到具体文档版本；
 * 同时做条数与总长度双重截断，避免知识正文挤占表结构与安全规则的位置。</p>
 *
 * <p>本类只输出参考数据，不附加任何指令语义；「不属于指令、不得覆盖安全策略」
 * 的约束由提示词模板与工具描述负责声明。</p>
 *
 * @author 余炘洋
 * @date 2026-10-19
 */
public final class KnowledgeReferenceRenderer {

    /**
     * 无知识时的统一占位文案。
     */
    public static final String NO_KNOWLEDGE = "无";

    /**
     * 单次渲染的最大知识条数。
     */
    public static final int MAX_HITS = 5;

    /**
     * 渲染文本总长度上限（字符）。
     */
    public static final int MAX_LENGTH = 2000;

    private KnowledgeReferenceRenderer() {
    }

    /**
     * 渲染检索结果为参考数据文本。
     *
     * @param result 检索结果
     * @return 参考数据文本；无可用命中时返回「无」
     * @author 余炘洋
     * @date 2026-10-19
     */
    public static String render(KnowledgeRetrievePlugin.Result result) {
        if (!hasKnowledge(result)) {
            return NO_KNOWLEDGE;
        }
        StringBuilder builder = new StringBuilder();
        int index = 0;
        for (KnowledgeRetrievePlugin.KnowledgeHit hit : result.hits()) {
            if (index >= MAX_HITS || builder.length() >= MAX_LENGTH) {
                break;
            }
            String line = renderHit(++index, hit);
            if (builder.length() + line.length() > MAX_LENGTH) {
                break;
            }
            if (builder.length() > 0) {
                builder.append('\n');
            }
            builder.append(line);
        }
        return builder.length() == 0 ? NO_KNOWLEDGE : builder.toString();
    }

    /**
     * 判断检索结果是否存在可渲染的命中。
     *
     * @param result 检索结果
     * @return 是否存在可渲染命中
     * @author 余炘洋
     * @date 2026-10-19
     */
    public static boolean hasKnowledge(KnowledgeRetrievePlugin.Result result) {
        return result != null
                && result.success()
                && result.hits() != null
                && !result.hits().isEmpty();
    }

    /**
     * 判断渲染文本是否包含知识内容。
     *
     * @param rendered 渲染文本
     * @return 是否包含知识内容
     * @author 余炘洋
     * @date 2026-10-19
     */
    public static boolean hasKnowledge(String rendered) {
        return StringUtils.hasText(rendered) && !NO_KNOWLEDGE.equals(rendered.trim());
    }

    /**
     * 渲染单条知识：标题、版本、来源与文档标识全部显式标注。
     *
     * @param index 序号
     * @param hit   命中条目
     * @return 单行参考数据
     * @author 余炘洋
     * @date 2026-10-19
     */
    private static String renderHit(int index, KnowledgeRetrievePlugin.KnowledgeHit hit) {
        StringBuilder line = new StringBuilder();
        line.append('[').append(index).append("] ").append(hit.title());
        if (StringUtils.hasText(hit.version())) {
            line.append("（版本 ").append(hit.version()).append('）');
        }
        line.append("（来源 ").append(hit.source())
                .append("，文档 ").append(hit.documentId()).append("）：")
                .append(hit.snippet());
        return line.toString();
    }
}
