package com.lingsql.acceptance;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * 黄金问题结果离线比较器，避免 Shell 浮点数和 JSON 重序列化造成财务数据误判。
 *
 * @author 余炘洋
 * @date 2026-08-15
 */
public final class GoldenQuestionResultComparator {

    private static final int EXIT_MISMATCH = 1;
    private static final int EXIT_INVALID_INPUT = 2;

    private final ObjectMapper objectMapper;

    private GoldenQuestionResultComparator() {
        this.objectMapper = JsonMapper.builder()
                .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                .enable(DeserializationFeature.USE_BIG_INTEGER_FOR_INTS)
                .build();
    }

    /**
     * 比较指定用例的冻结期望值与真实响应。
     *
     * @param args Suite 文件、用例 ID、响应文件
     */
    public static void main(String[] args) {
        if (args.length == 1 && "--build-commit".equals(args[0])) {
            printBuildCommit();
            return;
        }
        if (args.length != 3) {
            System.err.println("用法: GoldenQuestionResultComparator <suite.json> <caseId> <response.json>");
            System.exit(EXIT_INVALID_INPUT);
        }
        try {
            boolean matched = new GoldenQuestionResultComparator().compare(
                    Path.of(args[0]), args[1], Path.of(args[2]));
            if (!matched) {
                System.exit(EXIT_MISMATCH);
            }
        } catch (IllegalArgumentException | IOException ex) {
            System.err.println("黄金问题比较输入无效: " + ex.getMessage());
            System.exit(EXIT_INVALID_INPUT);
        }
    }

    private static void printBuildCommit() {
        try (InputStream input = GoldenQuestionResultComparator.class.getClassLoader()
                .getResourceAsStream("META-INF/build-info.properties")) {
            if (input == null) {
                throw new IllegalArgumentException("比较器制品缺少构建信息");
            }
            Properties properties = new Properties();
            properties.load(input);
            String commit = properties.getProperty("build.commit", "").trim();
            if (!commit.matches("[0-9a-f]{40}")) {
                throw new IllegalArgumentException("比较器制品未绑定有效 Git 提交");
            }
            System.out.println(commit);
        } catch (IOException | IllegalArgumentException ex) {
            System.err.println("读取比较器构建信息失败: " + ex.getMessage());
            System.exit(EXIT_INVALID_INPUT);
        }
    }

    private boolean compare(Path suiteFile, String caseId, Path responseFile) throws IOException {
        JsonNode suite = objectMapper.readTree(suiteFile.toFile());
        JsonNode response = objectMapper.readTree(responseFile.toFile());
        JsonNode businessCase = findBusinessCase(suite.path("businessCases"), caseId);
        JsonNode expected = businessCase.path("expected");
        JsonNode actualRows = response.path("data").path("data");
        if (!actualRows.isArray()) {
            return false;
        }
        return switch (expected.path("kind").asString()) {
            case "SCALAR_NUMBER" -> compareScalar(expected, actualRows);
            case "ROWSET" -> compareRowset(expected, actualRows);
            default -> throw new IllegalArgumentException("不支持的期望类型");
        };
    }

    private JsonNode findBusinessCase(JsonNode cases, String caseId) {
        if (!cases.isArray() || caseId == null || caseId.isBlank()) {
            throw new IllegalArgumentException("用例列表或用例 ID 无效");
        }
        for (JsonNode businessCase : cases) {
            if (caseId.equals(businessCase.path("id").asString())) {
                return businessCase;
            }
        }
        throw new IllegalArgumentException("Suite 中不存在用例: " + caseId);
    }

    private boolean compareScalar(JsonNode expected, JsonNode actualRows) {
        int rowIndex = expected.path("rowIndex").asInt(-1);
        String column = expected.path("column").asString();
        if (rowIndex < 0 || rowIndex >= actualRows.size() || column.isBlank()) {
            return false;
        }
        JsonNode actualNode = actualRows.get(rowIndex).get(column);
        if (actualNode == null || !actualNode.isNumber()) {
            return false;
        }
        BigDecimal expectedValue = requireNumber(expected.get("value"), "期望值");
        BigDecimal absoluteTolerance = requireNumber(expected.get("absoluteTolerance"), "绝对容差");
        BigDecimal relativeTolerance = requireNumber(expected.get("relativeTolerance"), "相对容差");
        BigDecimal difference = actualNode.decimalValue().subtract(expectedValue).abs();
        BigDecimal relativeLimit = expectedValue.abs().multiply(relativeTolerance);
        BigDecimal allowed = absoluteTolerance.max(relativeLimit);
        return difference.compareTo(allowed) <= 0;
    }

    private boolean compareRowset(JsonNode expected, JsonNode actualRows) {
        JsonNode columns = expected.path("columns");
        JsonNode expectedRows = expected.path("rows");
        if (!columns.isArray() || !expectedRows.isArray() || expectedRows.size() != actualRows.size()) {
            return false;
        }
        List<JsonNode> projectedExpected = projectRows(expectedRows, columns);
        List<JsonNode> projectedActual = projectRows(actualRows, columns);
        if (expected.path("orderSensitive").asBoolean(false)) {
            for (int index = 0; index < projectedExpected.size(); index++) {
                if (!equivalent(projectedExpected.get(index), projectedActual.get(index))) {
                    return false;
                }
            }
            return true;
        }
        boolean[] matched = new boolean[projectedActual.size()];
        for (JsonNode expectedRow : projectedExpected) {
            boolean found = false;
            for (int index = 0; index < projectedActual.size(); index++) {
                if (!matched[index] && equivalent(expectedRow, projectedActual.get(index))) {
                    matched[index] = true;
                    found = true;
                    break;
                }
            }
            if (!found) {
                return false;
            }
        }
        return true;
    }

    private List<JsonNode> projectRows(JsonNode rows, JsonNode columns) {
        List<JsonNode> projected = new ArrayList<>(rows.size());
        for (JsonNode row : rows) {
            if (!row.isObject()) {
                throw new IllegalArgumentException("结果集行必须是 JSON 对象");
            }
            var projectedRow = objectMapper.createObjectNode();
            for (JsonNode columnNode : columns) {
                String column = columnNode.asString();
                JsonNode value = row.get(column);
                if (column.isBlank() || value == null) {
                    throw new IllegalArgumentException("结果集缺少冻结列: " + column);
                }
                projectedRow.set(column, value);
            }
            projected.add(projectedRow);
        }
        return projected;
    }

    private boolean equivalent(JsonNode expected, JsonNode actual) {
        if (expected.isNumber() && actual.isNumber()) {
            return expected.decimalValue().compareTo(actual.decimalValue()) == 0;
        }
        if (expected.isObject() && actual.isObject()) {
            if (expected.size() != actual.size()) {
                return false;
            }
            for (var property : expected.properties()) {
                JsonNode actualValue = actual.get(property.getKey());
                if (actualValue == null || !equivalent(property.getValue(), actualValue)) {
                    return false;
                }
            }
            return true;
        }
        if (expected.isArray() && actual.isArray()) {
            if (expected.size() != actual.size()) {
                return false;
            }
            for (int index = 0; index < expected.size(); index++) {
                if (!equivalent(expected.get(index), actual.get(index))) {
                    return false;
                }
            }
            return true;
        }
        return expected.equals(actual);
    }

    private BigDecimal requireNumber(JsonNode node, String fieldName) {
        if (node == null || !node.isNumber()) {
            throw new IllegalArgumentException(fieldName + "必须是数字");
        }
        return node.decimalValue();
    }
}
