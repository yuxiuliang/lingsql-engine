package com.lingsql.agent.knowledge;

import com.lingsql.agent.orchestration.AgentCallerType;
import com.lingsql.agent.orchestration.AgentExecutionContext;
import com.lingsql.agent.orchestration.AgentRunControl;
import com.lingsql.agent.orchestration.AgentRunPolicy;
import com.lingsql.agent.plugin.AgentPluginExecutor;
import com.lingsql.agent.plugin.KnowledgeRetrievePlugin;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Set;

/**
 * 知识上下文服务：在 SQL 生成前经受控插件检索当前租户的业务知识，并渲染成可追溯的参考数据。
 *
 * <p>受控边界：</p>
 * <ul>
 *     <li>检索只经 {@code knowledge_retrieve} 插件网关执行，携带独立的
 *     {@link AgentRunPolicy}（只允许该插件、单次调用、固定成本单位与返回 deadline），
 *     未授权租户由网关 fail-closed 拒绝，不返回任何内容。</li>
 *     <li>渲染统一交给 {@link KnowledgeReferenceRenderer}，只做截断与溯源标注，不做内容改写，
 *     每条都带文档标识、版本与来源。</li>
 *     <li>任何异常、超时或无命中都降级为「无」，绝不阻断 SQL 生成，也绝不改变安全与权限判定。</li>
 * </ul>
 *
 * <p>插件网关延迟解析：SQL 生成服务本身位于插件注册表的下游（注册表持有 {@code smart_sql} 执行器），
 * 直接构造器注入网关会形成 Bean 循环依赖；这里只注入 {@link ObjectProvider}，在真正发起检索时才解析网关，
 * 网关不存在时同样按「无知识」降级。</p>
 *
 * @author 余炘洋
 * @date 2026-10-19
 */
@Slf4j
@Service
public class KnowledgeContextService {

    /**
     * 单次检索请求的召回条数。
     */
    public static final int RETRIEVE_TOP_K = 5;

    private static final long RETRIEVE_DEADLINE_MS = 10_000L;

    /**
     * 知识检索策略：只允许 knowledge_retrieve，单次调用，且带固定返回 deadline。
     */
    private static final AgentRunPolicy RETRIEVE_POLICY = new AgentRunPolicy(
            Set.of(KnowledgeRetrievePlugin.PLUGIN_ID),
            1,
            AgentCallerType.SYSTEM,
            3,
            3,
            RETRIEVE_DEADLINE_MS
    );

    private final ObjectProvider<AgentPluginExecutor> pluginExecutorProvider;

    public KnowledgeContextService(ObjectProvider<AgentPluginExecutor> pluginExecutorProvider) {
        this.pluginExecutorProvider = pluginExecutorProvider;
    }

    /**
     * 构建当前运行的租户知识上下文。
     *
     * @param context   Agent 执行上下文
     * @param userInput 用户问题
     * @return 带溯源标注的知识参考数据；无可用知识时返回「无」
     * @author 余炘洋
     * @date 2026-10-19
     */
    public String buildContext(AgentExecutionContext context, String userInput) {
        if (context == null || !StringUtils.hasText(context.tenantId()) || !StringUtils.hasText(userInput)) {
            return KnowledgeReferenceRenderer.NO_KNOWLEDGE;
        }
        AgentPluginExecutor pluginExecutor = pluginExecutorProvider.getIfAvailable();
        if (pluginExecutor == null) {
            return KnowledgeReferenceRenderer.NO_KNOWLEDGE;
        }
        KnowledgeRetrievePlugin.Result result;
        try {
            result = pluginExecutor.execute(
                    KnowledgeRetrievePlugin.PLUGIN_ID,
                    context,
                    new AgentRunControl(RETRIEVE_POLICY),
                    new KnowledgeRetrievePlugin.Request(userInput.trim(), RETRIEVE_TOP_K),
                    KnowledgeRetrievePlugin.Result.class
            );
        } catch (Exception exception) {
            // 说明：知识检索属于增强能力，失败必须降级而不是中断受控 SQL 生成。
            log.warn("知识检索失败，按无知识降级: tenantId={}, reason={}",
                    context.tenantId(), exception.getClass().getSimpleName());
            return KnowledgeReferenceRenderer.NO_KNOWLEDGE;
        }
        return KnowledgeReferenceRenderer.render(result);
    }

    /**
     * 暴露检索策略的插件白名单，便于管理端核对受控范围。
     *
     * @return 允许调用的插件标识
     * @author 余炘洋
     * @date 2026-10-19
     */
    public List<String> allowedPluginIds() {
        return List.copyOf(RETRIEVE_POLICY.allowedPluginIds());
    }
}
