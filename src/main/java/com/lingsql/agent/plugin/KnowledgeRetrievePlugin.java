package com.lingsql.agent.plugin;

import com.lingsql.agent.orchestration.AgentExecutionContext;
import com.lingsql.agent.orchestration.AgentRunControl;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * 受控知识检索插件：统一入口，强制租户、版本与权限过滤。
 *
 * <p>当前知识库尚未接入真实向量存储时，实现必须 fail-closed：不返回任何未授权内容，
 * 仅报告受控的“无可用语料”状态。知识能力完整接入前，禁止把检索结果视为系统安全策略的一部分。</p>
 *
 * @author 余炘洋
 * @date 2026-10-05
 */
@Service
public class KnowledgeRetrievePlugin implements IAgentPlugin<KnowledgeRetrievePlugin.Request, KnowledgeRetrievePlugin.Result> {

    public static final String PLUGIN_ID = "knowledge_retrieve";

    private static final AgentPluginDescriptor DESCRIPTOR = new AgentPluginDescriptor(
            PLUGIN_ID,
            "1.0.0",
            AgentPluginAccess.ORCHESTRATED,
            AgentPluginCost.MEDIUM,
            true,
            "受控知识检索（租户/权限/版本过滤）"
    );

    private final Optional<KnowledgeCorpusProvider> corpusProvider;

    public KnowledgeRetrievePlugin(Optional<KnowledgeCorpusProvider> corpusProvider) {
        this.corpusProvider = corpusProvider;
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
        return corpusProvider
                .map(p -> p.retrieve(context.tenantId(), input.query(), input.maxResults()))
                .orElse(new Result(false, "NO_CORPUS_CONFIGURED", List.of()));
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
     * 知识检索结果。
     *
     * @param success 是否成功
     * @param status  状态码
     * @param hits    命中条目
     */
    public record Result(boolean success, String status, List<String> hits) {
    }
}
