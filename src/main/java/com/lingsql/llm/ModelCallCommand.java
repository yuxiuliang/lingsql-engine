package com.lingsql.llm;

import com.lingsql.common.exception.BizException;
import com.lingsql.common.exception.ErrorCode;
import org.springframework.util.StringUtils;

/**
 * 一次逻辑模型调用命令。
 *
 * <p>命令显式携带租户、工作流和调用阶段，避免虚拟线程或恢复执行依赖 ThreadLocal。</p>
 *
 * @param tenantId 租户标识
 * @param workflowRequestId 工作流请求标识
 * @param callerType 调用方类型
 * @param callerId 调用方标识
 * @param stage 调用阶段
 * @param logicalCallNo 逻辑调用序号
 * @param systemPrompt 系统提示词
 * @param userPrompt 用户提示词
 * @author 余炘洋
 * @date 2026-08-15
 */
public record ModelCallCommand(
        String tenantId,
        String workflowRequestId,
        String callerType,
        String callerId,
        String stage,
        int logicalCallNo,
        String systemPrompt,
        String userPrompt
) {

    public ModelCallCommand {
        if (!StringUtils.hasText(tenantId)
                || !StringUtils.hasText(workflowRequestId)
                || !StringUtils.hasText(callerType)
                || !StringUtils.hasText(callerId)
                || !StringUtils.hasText(stage)
                || logicalCallNo <= 0
                || !StringUtils.hasText(systemPrompt)
                || !StringUtils.hasText(userPrompt)) {
            throw new BizException(ErrorCode.INVALID_PARAM, "模型调用上下文不完整");
        }
        tenantId = tenantId.trim();
        workflowRequestId = workflowRequestId.trim();
        callerType = callerType.trim();
        callerId = callerId.trim();
        stage = stage.trim();
    }
}
