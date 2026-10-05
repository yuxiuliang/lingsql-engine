package com.lingsql.agent.plugin;

import com.lingsql.agent.orchestration.AgentExecutionContext;
import com.lingsql.agent.orchestration.AgentRunControl;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 服务端图表映射插件：将受限结果集确定性地映射为 ECharts 配置。
 *
 * <p>仅做结构映射，不做渲染与外部请求，保证可重复与可审计。</p>
 *
 * @author 余炘洋
 * @date 2026-10-05
 */
@Service
public class ChartMapperPlugin implements IAgentPlugin<ChartMapperPlugin.Request, ChartMapperPlugin.Result> {

    public static final String PLUGIN_ID = "chart_mapper";

    private static final AgentPluginDescriptor DESCRIPTOR = new AgentPluginDescriptor(
            PLUGIN_ID,
            "1.0.0",
            AgentPluginAccess.ORCHESTRATED,
            AgentPluginCost.LOW,
            true,
            "将查询结果映射为服务端图表配置"
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
        if (input == null || input.rows() == null || input.rows().isEmpty()) {
            throw new AgentPluginException(AgentPluginFailureReason.CONTRACT_VIOLATION, "结果集不能为空");
        }
        control.assertWithinDeadline();
        Map<String, Object> firstRow = input.rows().get(0);
        List<String> columns = new ArrayList<>(firstRow.keySet());
        if (columns.size() < 2) {
            throw new AgentPluginException(AgentPluginFailureReason.CONTRACT_VIOLATION, "结果集至少需要两列（维度与指标）");
        }
        // 确定性启发式：所有取值均为数字的列作为指标，其余第一列作为维度。
        String measureColumn = null;
        for (String column : columns) {
            boolean allNumeric = true;
            for (Map<String, Object> row : input.rows()) {
                if (!(row.get(column) instanceof Number)) {
                    allNumeric = false;
                    break;
                }
            }
            if (allNumeric) {
                measureColumn = column;
                break;
            }
        }
        if (measureColumn == null) {
            throw new AgentPluginException(AgentPluginFailureReason.CONTRACT_VIOLATION, "未找到可作为指标的数值列");
        }
        String categoryColumn = null;
        for (String column : columns) {
            if (!column.equals(measureColumn)) {
                categoryColumn = column;
                break;
            }
        }
        final String category = categoryColumn;
        final String measure = measureColumn;
        List<String> categories = new ArrayList<>();
        List<Object> measures = new ArrayList<>();
        for (Map<String, Object> row : input.rows()) {
            categories.add(String.valueOf(row.get(category)));
            measures.add(row.get(measure));
        }
        Map<String, Object> option = new LinkedHashMap<>();
        option.put("title", Map.of("text", input.title() == null ? "" : input.title()));
        option.put("tooltip", Map.of());
        option.put("xAxis", Map.of("type", "category", "data", categories));
        option.put("yAxis", Map.of("type", "value"));
        option.put("series", List.of(Map.of(
                "name", measureColumn,
                "type", input.chartType() == null ? "bar" : input.chartType(),
                "data", measures
        )));
        return new Result(true, option);
    }

    /**
     * 图表映射请求。
     *
     * @param title     图表标题
     * @param chartType 图表类型（默认 bar）
     * @param rows      受限结果集
     */
    public record Request(String title, String chartType, List<Map<String, Object>> rows) {
    }

    /**
     * 图表映射结果。
     *
     * @param success 是否成功
     * @param option  ECharts 配置
     */
    public record Result(boolean success, Map<String, Object> option) {
    }
}
