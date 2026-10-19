package com.lingsql.agent.plugin;

import com.lingsql.agent.orchestration.AgentExecutionContext;
import com.lingsql.agent.orchestration.AgentRunControl;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 受控知识检索插件：统一入口，强制租户、版本与权限过滤。
 *
 * <p>检索结果的每一条都必须带文档标识、标题、版本与来源，缺失溯源的条目一律丢弃，
 * 以保证注入模型的每段知识都能被追溯到具体文档版本；知识内容只作为参考数据，
 * 不得覆盖系统安全策略，也不得作为数据权限依据。</p>
 *
 * <p>语料提供者按 Spring 装配顺序取第一个可用实现：配置向量库时优先使用向量语义检索，
 * 否则回落到数据库关键词检索；两者都不可用时 fail-closed，只报告受控的“无可用语料”状态。</p>
 *
 * @author 余炘洋
 * @date 2026-10-05
 */
@Service
public class KnowledgeRetrievePlugin implements IAgentPlugin<KnowledgeRetrievePlugin.Request, KnowledgeRetrievePlugin.Result> {

    public static final String PLUGIN_ID = "knowledge_retrieve";

    /**
     * 单次检索返回条数上限。
     */
    public static final int MAX_RESULTS = 20;

    /**
     * 单条命中的正文最大长度（字符），超出部分截断。
     */
    public static final int MAX_SNIPPET_LENGTH = 500;

    /**
     * 数据来源：数据库关键词检索。
     */
    public static final String SOURCE_DB_CORPUS = "DB_CORPUS";

    /**
     * 数据来源：向量语义检索。
     */
    public static final String SOURCE_VECTOR_STORE = "VECTOR_STORE";

    private static final AgentPluginDescriptor DESCRIPTOR = new AgentPluginDescriptor(
            PLUGIN_ID,
            "1.1.0",
            AgentPluginAccess.ORCHESTRATED,
            AgentPluginCost.MEDIUM,
            true,
            "受控知识检索（租户/权限/版本过滤）"
    );

    private final List<KnowledgeCorpusProvider> corpusProviders;

    public KnowledgeRetrievePlugin(List<KnowledgeCorpusProvider> corpusProviders) {
        this.corpusProviders = corpusProviders == null ? List.of() : List.copyOf(corpusProviders);
    }

    @Override
    public AgentPluginDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public Class<Request> inputType() {
        return Request.class;
    }

    @Override
    public Class<Result> outputType() {
        return Result.class;
    }

    @Override
    public Result execute(AgentExecutionContext context, AgentRunControl control, Request input) {
        if (input == null || input.query() == null || input.query().isBlank()) {
            throw new AgentPluginException(AgentPluginFailureReason.CONTRACT_VIOLATION, "检索查询不能为空");
        }
        control.assertWithinDeadline();
        if (context == null || !StringUtils.hasText(context.tenantId())) {
            throw new AgentPluginException(AgentPluginFailureReason.CONTRACT_VIOLATION, "知识检索必须显式携带租户标识");
        }
        KnowledgeCorpusProvider provider = resolveProvider();
        if (provider == null) {
            return new Result(false, "NO_CORPUS_CONFIGURED", List.of());
        }
        int topK = Math.min(Math.max(input.maxResults(), 1), MAX_RESULTS);
        Result result = provider.retrieve(context.tenantId(), input.query(), topK);
        if (result == null || result.hits() == null) {
            return new Result(false, "NO_HIT", List.of());
        }
        List<KnowledgeHit> sanitized = result.hits().stream()
                .filter(KnowledgeRetrievePlugin::traceable)
                .limit(topK)
                .toList();
        String status = StringUtils.hasText(result.status())
                ? result.status()
                : (sanitized.isEmpty() ? "NO_HIT" : "OK");
        return new Result(!sanitized.isEmpty(), status, sanitized);
    }

    /**
     * 选取语料提供者：取装配顺序中的第一个实现。
     *
     * @return 语料提供者，未装配时为 null
     * @author 余炘洋
     * @date 2026-10-19
     */
    private KnowledgeCorpusProvider resolveProvider() {
        for (KnowledgeCorpusProvider provider : corpusProviders) {
            if (provider != null) {
                return provider;
            }
        }
        return null;
    }

    /**
     * 校验命中条目是否具备可追溯的溯源信息。
     *
     * @param hit 命中条目
     * @return 是否允许返回
     * @author 余炘洋
     * @date 2026-10-19
     */
    private static boolean traceable(KnowledgeHit hit) {
        return hit != null
                && StringUtils.hasText(hit.documentId())
                && StringUtils.hasText(hit.title())
                && StringUtils.hasText(hit.source())
                && StringUtils.hasText(hit.snippet());
    }

    /**
     * 截断正文并补齐来源标注，保证每条知识都能追溯到具体文档版本。
     *
     * @param documentId 文档标识
     * @param title      标题
     * @param version    版本
     * @param source     来源
     * @param snippet    正文
     * @return 规范化后的命中条目
     * @author 余炘洋
     * @date 2026-10-19
     */
    public static KnowledgeHit toHit(String documentId, String title, String version, String source, String snippet) {
        String normalizedId = documentId == null ? null : documentId.trim();
        String normalizedTitle = title == null ? "" : title.trim();
        String normalizedVersion = version == null ? "" : version.trim();
        String normalizedSource = source == null ? "" : source.trim();
        String normalizedSnippet = snippet == null ? "" : snippet.trim();
        if (normalizedSnippet.length() > MAX_SNIPPET_LENGTH) {
            normalizedSnippet = normalizedSnippet.substring(0, MAX_SNIPPET_LENGTH) + "…";
        }
        return new KnowledgeHit(normalizedId, normalizedTitle, normalizedVersion, normalizedSource, normalizedSnippet);
    }

    /**
     * 知识语料提供者：完整 RAG 接入前的防腐层接口。
     */
    public interface KnowledgeCorpusProvider {
        /**
         * 按租户执行受控检索。
         *
         * @param tenantId   租户标识
         * @param query      查询
         * @param maxResults 最大返回条数
         * @return 检索结果
         */
        Result retrieve(String tenantId, String query, int maxResults);
    }

    /**
     * 知识检索请求。
     *
     * @param query      查询文本
     * @param maxResults 最大返回条数
     */
    public record Request(String query, int maxResults) {
    }

    /**
     * 检索命中条目。
     *
     * @param documentId 文档标识
     * @param title      文档标题
     * @param version    文档版本
     * @param source     来源（DB_CORPUS 或 VECTOR_STORE）
     * @param snippet    正文片段
     */
    public record KnowledgeHit(String documentId, String title, String version, String source, String snippet) {
    }

    /**
     * 知识检索结果。
     *
     * @param success 是否成功
     * @param status  状态码
     * @param hits    命中条目，均带溯源信息
     */
    public record Result(boolean success, String status, List<KnowledgeHit> hits) {
    }
}
