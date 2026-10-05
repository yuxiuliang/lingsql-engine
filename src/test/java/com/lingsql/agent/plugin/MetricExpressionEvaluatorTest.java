package com.lingsql.agent.plugin;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 受控指标表达式求值器单元测试：算术正确性与安全边界。
 *
 * @author 余炘洋
 * @date 2026-10-05
 */
class MetricExpressionEvaluatorTest {

    private double eval(String expression) {
        return MetricExpressionEvaluator.evaluate(expression, Map.of());
    }

    private double eval(String expression, Map<String, Object> variables) {
        return MetricExpressionEvaluator.evaluate(expression, variables);
    }

    @Test
    void evaluatesArithmetic() {
        assertEquals(7d, eval("1+2*3"), 1e-9);
        assertEquals(9d, eval("(1+2)*3"), 1e-9);
        assertEquals(1d, eval("7 % 3"), 1e-9);
        assertEquals(2.5d, eval("5/2"), 1e-9);
        assertEquals(-5d, eval("-5"), 1e-9);
        assertEquals(3d, eval("10 - 3 - 4"), 1e-9);
        assertEquals(14d, eval("2 * (3 + 4)"), 1e-9);
    }

    @Test
    void handlesWhitespaceAndDecimals() {
        assertEquals(3d, eval("  1 +   2  "), 1e-9);
        assertEquals(0.3d, eval("0.1 + 0.2"), 1e-9);
    }

    @Test
    void resolvesVariables() {
        assertEquals(7d, eval("a + b * 2", Map.of("a", 1, "b", 3)), 1e-9);
        assertEquals(50d, eval("amount * rate", Map.of("amount", 100, "rate", 0.5)), 1e-9);
        assertEquals(3d, eval("n + 1", Map.of("n", 2.0)), 1e-9);
        assertEquals(4d, eval("v * 2", Map.of("v", 2L)), 1e-9);
    }

    @Test
    void rejectsEmptyExpression() {
        assertThrows(IllegalArgumentException.class, () -> eval(null));
        assertThrows(IllegalArgumentException.class, () -> eval(""));
        assertThrows(IllegalArgumentException.class, () -> eval("   "));
    }

    @Test
    void rejectsDivisionByZero() {
        assertThrows(IllegalArgumentException.class, () -> eval("1/0"));
        assertThrows(IllegalArgumentException.class, () -> eval("1 % 0"));
    }

    @Test
    void rejectsUnbalancedParentheses() {
        assertThrows(IllegalArgumentException.class, () -> eval("(1+2"));
        assertThrows(IllegalArgumentException.class, () -> eval("1+2)"));
    }

    @Test
    void rejectsMissingVariable() {
        assertThrows(IllegalArgumentException.class, () -> eval("a + b", Map.of("a", 1)));
    }

    @Test
    void rejectsNonNumericVariableValue() {
        assertThrows(IllegalArgumentException.class, () -> eval("v + 1", Map.of("v", "abc")));
        java.util.Map<String, Object> withNull = new java.util.HashMap<>();
        withNull.put("v", null);
        assertThrows(IllegalArgumentException.class, () -> eval("v + 1", withNull));
    }

    @Test
    void rejectsNonFiniteVariableValue() {
        assertThrows(IllegalArgumentException.class, () -> eval("v", Map.of("v", Double.NaN)));
        assertThrows(IllegalArgumentException.class, () -> eval("v", Map.of("v", Double.POSITIVE_INFINITY)));
    }

    @Test
    void rejectsTrailingGarbage() {
        assertThrows(IllegalArgumentException.class, () -> eval("1+2)3"));
        assertThrows(IllegalArgumentException.class, () -> eval("1 2"));
    }

    @Test
    void rejectsIllegalCharacters() {
        assertThrows(IllegalArgumentException.class, () -> eval("1 & 2"));
        assertThrows(IllegalArgumentException.class, () -> eval("a.b"));
        assertThrows(IllegalArgumentException.class, () -> eval("1;2"));
    }

    @Test
    void rejectsOverlongExpression() {
        String longExpression = "1+".repeat(400) + "1";
        assertThrows(IllegalArgumentException.class, () -> eval(longExpression));
    }

    @Test
    void rejectsNonFiniteResult() {
        assertThrows(IllegalArgumentException.class, () -> eval("1e400"));
    }

    @Test
    void rejectsMethodCallLikeSyntax() {
        // 无方法调用能力：括号只能是表达式分组，字母只能构成变量名
        assertThrows(IllegalArgumentException.class, () -> eval("runtime()"));
        assertThrows(IllegalArgumentException.class, () -> eval("java.lang.Runtime"));
        assertThrows(IllegalArgumentException.class, () -> eval("T(java.lang.Runtime)"));
    }
}