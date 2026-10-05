package com.lingsql.agent.plugin;

import com.lingsql.agent.orchestration.AgentExecutionContext;
import com.lingsql.agent.orchestration.AgentRunControl;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 指标计算插件：对受控数值表达式求值，用于统一指标口径计算。
 *
 * <p><b>安全边界</b>：使用项目内置的 {@link MetricExpressionEvaluator}（递归下降解析），
 * 不引入第三方表达式引擎、不使用 SpEL，不提供方法调用、类型引用或反射能力。
 * 表达式即使来自模型输出，也无法触达文件系统、网络或宿主对象。</p>
 *
 * @author 余炘洋
 * @date 2026-10-05
 */
@Service
public class CalculateMetricPlugin implements IAgentPlugin<CalculateMetricPlugin.Request, CalculateMetricPlugin.Result> {

    public static final String PLUGIN_ID = "calculate_metric";

    private static final AgentPluginDescriptor DESCRIPTOR = new AgentPluginDescriptor(
            PLUGIN_ID,
            "1.1.0",
            AgentPluginAccess.ORCHESTRATED,
            AgentPluginCost.LOW,
            true,
            "对受控数值表达式求值以统一指标口径"
    );

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
        if (input == null || input.expression() == null || input.expression().isBlank()) {
            throw new AgentPluginException(AgentPluginFailureReason.CONTRACT_VIOLATION, "指标表达式不能为空");
        }
        control.assertWithinDeadline();
        try {
            double value = MetricExpressionEvaluator.evaluate(
                    input.expression(), input.variables() == null ? Map.of() : input.variables());
            return new Result(true, value, null);
        } catch (IllegalArgumentException exception) {
            throw new AgentPluginException(AgentPluginFailureReason.PLUGIN_FAILED,
                    "指标计算失败: " + exception.getMessage());
        }
    }

    /**
     * 指标计算请求。
     *
     * @param expression 数值表达式（仅支持 + - * / %、括号与变量引用）
     * @param variables  变量映射，取值必须为有限数值
     */
    public record Request(String expression, Map<String, Object> variables) {
    }

    /**
     * 指标计算结果。
     *
     * @param success 是否成功
     * @param value   计算结果
     * @param error   错误消息（失败时）
     */
    public record Result(boolean success, Object value, String error) {
    }
}