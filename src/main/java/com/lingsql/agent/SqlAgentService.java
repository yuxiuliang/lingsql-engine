package com.lingsql.agent;

import com.lingsql.agent.prompt.SqlPromptTemplate;
import com.lingsql.agent.orchestration.AgentExecutionContext;
import com.lingsql.chat.service.ChatMemoryService;
import com.lingsql.common.exception.BizException;
import com.lingsql.common.exception.ErrorCode;
import com.lingsql.config.LingSqlProperties;
import com.lingsql.datasource.DataSourceManager;
import com.lingsql.datasource.TenantDbConfigErrors;
import com.lingsql.datasource.model.TenantDbConfig;
import com.lingsql.datasource.repository.TenantDbConfigRepository;
import com.lingsql.llm.IModelGateway;
import com.lingsql.llm.ModelCallCommand;
import com.lingsql.llm.ModelCallResult;
import com.lingsql.schema.MetadataCacheService;
import com.lingsql.schema.SchemaScanner;
import com.lingsql.schema.SemanticLexicon;
import com.lingsql.schema.model.TableInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;
import java.util.List;

/**
 * SQL 生成核心服务，负责 Prompt 拼装与 LLM 交互。
 *
 * @author 余炘洋
 * @date 2026-01-22
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SqlAgentService {

    private static final String MODEL_CALLER_TYPE = "AGENT";
    private static final String MODEL_STAGE = "SQL_ARCHITECT";

    private final IModelGateway modelGateway;
    private final MetadataCacheService metadataCacheService;
    private final SqlPromptTemplate sqlPromptTemplate;
    private final SemanticLexicon semanticLexicon;
    private final com.lingsql.schema.SemanticContextVersionService semanticContextVersionService;
    private final SchemaScanner schemaScanner;
    private final DataSourceManager dataSourceManager;
    private final TenantDbConfigRepository tenantDbConfigRepository;
    private final LingSqlProperties properties;
    private final ChatMemoryService chatMemoryService;

    /**
     * 将自然语言转换为 SQL。
     *
     * @param context Agent 执行上下文
     * @param userInput 用户问题
     * @param logicalCallNo 逻辑调用序号
     * @return 模型调用结果
     * @author 余炘洋
     * @date 2026-01-22
     */
    public ModelCallResult generateSql(AgentExecutionContext context, String userInput, int logicalCallNo) {
        return generateSqlInternal(context, userInput, null, logicalCallNo);
    }

    /**
     * 失败重试时使用的 SQL 生成方法。
     *
     * @param context Agent 执行上下文
     * @param userInput 用户问题
     * @param reason    失败原因
     * @param logicalCallNo 逻辑调用序号
     * @return 模型调用结果
     * @author 余炘洋
     * @date 2026-01-22
     */
    public ModelCallResult generateSqlWithRetry(
            AgentExecutionContext context,
            String userInput,
            String reason,
            int logicalCallNo
    ) {
        return generateSqlInternal(context, userInput, reason, logicalCallNo);
    }

    /**
     * 失配恢复时强制刷新当前租户的 Schema 缓存。
     *
     * @param tenantId 租户标识
     * @author 余炘洋
     * @date 2026-07-29
     */
    public void refreshSchema(String tenantId) {
        if (!StringUtils.hasText(tenantId)) {
            throw new BizException(ErrorCode.INVALID_PARAM, "租户ID不能为空");
        }
        TenantDbConfig config = tenantDbConfigRepository.findByTenantId(tenantId)
                .orElseThrow(TenantDbConfigErrors::missingConfig);
        metadataCacheService.evictSchema(tenantId);
        DataSource dataSource = dataSourceManager.getDataSource(tenantId);
        List<TableInfo> tables = schemaScanner.scanSchema(tenantId, dataSource, config.getWhiteListTables());
        if (tables.isEmpty()) {
            throw new BizException(ErrorCode.CONFIG_INVALID, "当前租户未扫描到可用表结构，无法继续自动纠错");
        }
        metadataCacheService.cacheSchema(tenantId, tables);
    }

    /**
     * 内部 SQL 生成入口，支持重试场景。
     *
     * @param context Agent 执行上下文
     * @param userInput 用户问题
     * @param reason    失败原因
     * @param logicalCallNo 逻辑调用序号
     * @return 模型调用结果
     * @author 余炘洋
     * @date 2026-01-22
     */
    private ModelCallResult generateSqlInternal(
            AgentExecutionContext context,
            String userInput,
            String reason,
            int logicalCallNo
    ) {
        if (context == null || !StringUtils.hasText(context.tenantId())) {
            throw new BizException(ErrorCode.INVALID_PARAM, "Agent 模型调用上下文不完整");
        }
        String tenantId = context.tenantId();
        validatePrompt(userInput);

        TenantDbConfig config = tenantDbConfigRepository.findByTenantId(tenantId)
                .orElseThrow(TenantDbConfigErrors::missingConfig);

        List<TableInfo> tables = metadataCacheService.getCachedSchema(tenantId);
        if (tables == null || tables.isEmpty()) {
            DataSource dataSource = dataSourceManager.getDataSource(tenantId);
            tables = schemaScanner.scanSchema(tenantId, dataSource, config.getWhiteListTables());
            metadataCacheService.cacheSchema(tenantId, tables);
        }

        String schemaInfo = metadataCacheService.getAiFriendlySchema(tenantId, tables);
        String lexiconInfo = semanticLexicon.renderLexicon(tenantId);
        lexiconInfo = "语义上下文版本：" + semanticContextVersionService.currentVersionId(tenantId) + "\n" + lexiconInfo;
        String joinInfo = semanticLexicon.renderJoinRules(tenantId);
        String ruleInfo = buildRuleInfo(config);

        String historyPrompt = chatMemoryService.getHistoryPrompt();
        if (!StringUtils.hasText(historyPrompt)) {
            historyPrompt = "无";
        }
        String systemPrompt = sqlPromptTemplate.buildSystemPrompt(schemaInfo, lexiconInfo, joinInfo, ruleInfo, historyPrompt);
        String finalUserInput = buildUserPrompt(userInput, reason);

        log.info("AI 正在生成租户 [{}] 的 SQL...", tenantId);
        ModelCallResult result = modelGateway.chat(new ModelCallCommand(
                tenantId,
                context.requestId(),
                MODEL_CALLER_TYPE,
                context.executionId(),
                MODEL_STAGE,
                logicalCallNo,
                systemPrompt,
                finalUserInput
        ));
        return result.withContent(cleanSql(result.content()));
    }

    /**
     * 构建 SQL 生成规则说明。
     *
     * @param config 租户配置
     * @return 规则文本
     * @author 余炘洋
     * @date 2026-01-22
     */
    private String buildRuleInfo(TenantDbConfig config) {
        StringBuilder sb = new StringBuilder();
        sb.append("- 只能使用允许的表和字段，不得访问敏感字段。\n");
        sb.append("- 仅允许 SELECT，不得生成写入语句。\n");
        sb.append("- 必须包含 LIMIT ").append(properties.getSecurity().getDefaultLimit()).append("。\n");
        if (config.isLogicalIsolation()) {
            sb.append("- 逻辑隔离模式必须包含条件：")
                    .append(config.resolveLogicColumn())
                    .append(" = '").append(config.getTenantId()).append("'。\n");
        }
        sb.append("- 不允许使用未提供的 Join 关系。\n");
        return sb.toString();
    }

    /**
     * 构建用户提示词。
     *
     * @param userInput 用户问题
     * @param reason    失败原因
     * @return 提示词
     * @author 余炘洋
     * @date 2026-01-22
     */
    private String buildUserPrompt(String userInput, String reason) {
        if (!StringUtils.hasText(reason)) {
            return userInput;
        }
        return "上一次生成失败，原因：" + reason + "。请重新生成 SQL。用户问题：" + userInput;
    }

    /**
     * Prompt 注入检测。
     *
     * @param userInput 用户问题
     * @author 余炘洋
     * @date 2026-01-22
     */
    private void validatePrompt(String userInput) {
        if (!StringUtils.hasText(userInput)) {
            throw new BizException(ErrorCode.INVALID_PARAM, "用户问题不能为空");
        }
        String lowerInput = userInput.toLowerCase();
        for (String keyword : properties.getSecurity().getPromptInjectionKeywords()) {
            if (StringUtils.hasText(keyword) && lowerInput.contains(keyword.toLowerCase())) {
                throw new BizException(ErrorCode.SECURITY_VIOLATION, "检测到疑似 Prompt 注入指令，请重新描述需求");
            }
        }
    }

    /**
     * 清洗模型输出的 SQL 文本。
     *
     * @param sql 模型输出
     * @return 清洗结果
     * @author 余炘洋
     * @date 2026-01-22
     */
    private String cleanSql(String sql) {
        if (sql == null) {
            return "";
        }
        return sql.replace("```sql", "").replace("```", "").trim();
    }
}
