package com.lingsql.chat.service;

import com.lingsql.agent.orchestration.AgentRunCommand;
import com.lingsql.agent.orchestration.AgentRunResult;
import com.lingsql.agent.orchestration.IControlledAgentOrchestrator;
import com.lingsql.chat.entity.ChatHistory;
import com.lingsql.chat.model.ChatRequest;
import com.lingsql.chat.model.ChatResult;
import com.lingsql.common.exception.BizException;
import com.lingsql.common.exception.ErrorCode;
import com.lingsql.common.trace.RequestTraceContext;
import com.lingsql.context.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.UUID;

/**
 * 问答业务编排服务。
 *
 * @author 余炘洋
 * @date 2026-01-28
 */
@Service
@RequiredArgsConstructor
public class ChatApplicationService {

    private static final String QUESTION_REQUIRED_MESSAGE = "问题不能为空";

    private final IControlledAgentOrchestrator agentOrchestrator;
    private final ChatHistoryService chatHistoryService;
    private final ChatMemoryService chatMemoryService;

    /**
     * 处理问答请求。
     *
     * @param request 请求体
     * @return 问答结果
     * @author 余炘洋
     * @date 2026-01-28
     */
    public ChatResult ask(ChatRequest request) {
        if (request == null || !StringUtils.hasText(request.getQuestion())) {
            throw new BizException(ErrorCode.INVALID_PARAM, QUESTION_REQUIRED_MESSAGE);
        }
        String question = request.getQuestion().trim();
        String tenantId = TenantContext.getTenantId();
        Long userId = TenantContext.getUserId();
        String conversationId = resolveConversationId(request.getConversationId());
        TenantContext.setConversationId(conversationId);
        AgentRunResult runResult = agentOrchestrator.run(new AgentRunCommand(
                tenantId,
                userId,
                conversationId,
                question,
                "WEB",
                RequestTraceContext.resolveRequestId()
        ));
        if (runResult.querySucceeded()) {
            chatMemoryService.saveSuccessExchange(
                    tenantId,
                    question,
                    runResult.answer(),
                    runResult.sql(),
                    runResult.data()
            );
        }
        ChatHistory history = chatHistoryService.saveHistory(
                tenantId,
                userId,
                conversationId,
                question,
                runResult.answer(),
                runResult.sql(),
                runResult.data(),
                runResult.trace()
        );
        return new ChatResult(
                runResult.answer(),
                runResult.data(),
                runResult.sql(),
                history.getId(),
                runResult.trace(),
                conversationId,
                runResult.failureCode()
        );
    }

    private String resolveConversationId(String conversationId) {
        if (StringUtils.hasText(conversationId)) {
            return conversationId.trim();
        }
        return UUID.randomUUID().toString();
    }

}
