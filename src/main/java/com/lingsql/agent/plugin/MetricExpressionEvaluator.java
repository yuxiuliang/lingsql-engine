package com.lingsql.agent.plugin;

import java.util.Map;

/**
 * 受控指标表达式求值器：递归下降解析，仅支持数值字面量、四则运算、括号与变量引用。
 *
 * <p><b>安全边界</b>：不引入第三方表达式引擎，不使用 SpEL，不提供任何方法调用、
 * 类型引用、属性访问或反射能力。变量只能从调用方传入的 {@code Map} 中按键取值，
 * 且取值必须是有限数值。因此表达式即使来自模型输出，也无法触达文件系统、网络或反射。</p>
 *
 * <p>支持语法：{@code + - * / %}、一元正负号、括号、数字字面量、变量名（字母/数字/下划线）。</p>
 *
 * @author 余炘洋
 * @date 2026-10-05
 */
public final class MetricExpressionEvaluator {

    /** 表达式最大长度，防御超长输入。 */
    private static final int MAX_EXPRESSION_LENGTH = 512;

    /** 变量名最大长度。 */
    private static final int MAX_VARIABLE_NAME_LENGTH = 64;

    private final String source;
    private final Map<String, Object> variables;
    private int position;

    private MetricExpressionEvaluator(String source, Map<String, Object> variables) {
        this.source = source;
        this.variables = variables;
    }

    /**
     * 对表达式求值。
     *
     * @param expression 表达式文本
     * @param variables  变量映射
     * @return 计算结果（Double）
     * @throws IllegalArgumentException 表达式非法或变量缺失
     */
    public static double evaluate(String expression, Map<String, Object> variables) {
        if (expression == null || expression.isBlank()) {
            throw new IllegalArgumentException("指标表达式不能为空");
        }
        if (expression.length() > MAX_EXPRESSION_LENGTH) {
            throw new IllegalArgumentException("指标表达式长度超过上限: " + MAX_EXPRESSION_LENGTH);
        }
        Map<String, Object> safeVariables = variables == null ? Map.of() : variables;
        MetricExpressionEvaluator evaluator = new MetricExpressionEvaluator(expression, safeVariables);
        double result = evaluator.parseExpression();
        evaluator.skipWhitespace();
        if (evaluator.position < evaluator.source.length()) {
            throw new IllegalArgumentException("指标表达式存在多余字符，位置: " + evaluator.position);
        }
        if (!Double.isFinite(result)) {
            throw new IllegalArgumentException("指标表达式计算结果不是有限数值");
        }
        return result;
    }

    /** expression := term { ('+' | '-') term } */
    private double parseExpression() {
        double value = parseTerm();
        while (true) {
            skipWhitespace();
            if (consume('+')) {
                value = value + parseTerm();
            } else if (consume('-')) {
                value = value - parseTerm();
            } else {
                return value;
            }
        }
    }

    /** term := factor { ('*' | '/' | '%') factor } */
    private double parseTerm() {
        double value = parseFactor();
        while (true) {
            skipWhitespace();
            if (consume('*')) {
                value = value * parseFactor();
            } else if (consume('/')) {
                double divisor = parseFactor();
                if (divisor == 0d) {
                    throw new IllegalArgumentException("指标表达式出现除零");
                }
                value = value / divisor;
            } else if (consume('%')) {
                double divisor = parseFactor();
                if (divisor == 0d) {
                    throw new IllegalArgumentException("指标表达式出现取模零");
                }
                value = value % divisor;
            } else {
                return value;
            }
        }
    }

    /** factor := ('+' | '-') factor | primary */
    private double parseFactor() {
        skipWhitespace();
        if (consume('+')) {
            return parseFactor();
        }
        if (consume('-')) {
            return -parseFactor();
        }
        return parsePrimary();
    }

    /** primary := number | variable | '(' expression ')' */
    private double parsePrimary() {
        skipWhitespace();
        if (position >= source.length()) {
            throw new IllegalArgumentException("指标表达式意外结束");
        }
        char current = source.charAt(position);
        if (current == '(') {
            position++;
            double value = parseExpression();
            skipWhitespace();
            if (!consume(')')) {
                throw new IllegalArgumentException("指标表达式括号不匹配，位置: " + position);
            }
            return value;
        }
        if (Character.isDigit(current) || current == '.') {
            return parseNumber();
        }
        if (isVariableStart(current)) {
            return parseVariable();
        }
        throw new IllegalArgumentException("指标表达式存在非法字符: " + current);
    }

    private double parseNumber() {
        int start = position;
        boolean seenDot = false;
        while (position < source.length()) {
            char c = source.charAt(position);
            if (Character.isDigit(c)) {
                position++;
            } else if (c == '.' && !seenDot) {
                seenDot = true;
                position++;
            } else {
                break;
            }
        }
        String literal = source.substring(start, position);
        try {
            return Double.parseDouble(literal);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("指标表达式数字字面量非法: " + literal);
        }
    }

    private double parseVariable() {
        int start = position;
        while (position < source.length() && isVariablePart(source.charAt(position))) {
            position++;
        }
        String name = source.substring(start, position);
        if (name.length() > MAX_VARIABLE_NAME_LENGTH) {
            throw new IllegalArgumentException("指标表达式变量名长度超过上限: " + MAX_VARIABLE_NAME_LENGTH);
        }
        if (!variables.containsKey(name)) {
            throw new IllegalArgumentException("指标表达式引用了未提供的变量: " + name);
        }
        Object raw = variables.get(name);
        if (!(raw instanceof Number number)) {
            throw new IllegalArgumentException("指标表达式变量必须是数值: " + name);
        }
        double value = number.doubleValue();
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("指标表达式变量取值不是有限数值: " + name);
        }
        return value;
    }

    private boolean isVariableStart(char c) {
        return Character.isLetter(c) || c == '_';
    }

    private boolean isVariablePart(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }

    private boolean consume(char expected) {
        if (position < source.length() && source.charAt(position) == expected) {
            position++;
            return true;
        }
        return false;
    }

    private void skipWhitespace() {
        while (position < source.length() && Character.isWhitespace(source.charAt(position))) {
            position++;
        }
    }
}