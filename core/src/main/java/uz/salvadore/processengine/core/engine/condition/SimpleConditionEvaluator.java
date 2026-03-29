package uz.salvadore.processengine.core.engine.condition;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Simple condition evaluator supporting expressions like:
 * ${variable > value}, ${variable < value}, ${variable == value},
 * ${variable >= value}, ${variable <= value}, ${variable != value}
 */
public final class SimpleConditionEvaluator implements ConditionEvaluator {

    private static final Pattern EXPRESSION_PATTERN =
            Pattern.compile("^\\$\\{\\s*(.+?)\\s*(==|!=|>=|<=|>|<)\\s*(.+?)\\s*}$");

    @Override
    public boolean evaluate(String expression, Map<String, Object> variables) {
        Matcher matcher = EXPRESSION_PATTERN.matcher(expression.trim());
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid condition expression: " + expression);
        }

        String variableName = matcher.group(1).trim();
        String operator = matcher.group(2).trim();
        String rawValue = matcher.group(3).trim();

        Object variableValue = variables.get(variableName);
        if (variableValue == null) {
            throw new IllegalArgumentException("Variable '" + variableName + "' not found in process variables");
        }

        Object parsedValue = parseValue(rawValue);

        return compare(variableValue, operator, parsedValue);
    }

    private Object parseValue(String raw) {
        if ("true".equalsIgnoreCase(raw)) {
            return Boolean.TRUE;
        }
        if ("false".equalsIgnoreCase(raw)) {
            return Boolean.FALSE;
        }
        if (raw.startsWith("'") && raw.endsWith("'")) {
            return raw.substring(1, raw.length() - 1);
        }
        if (raw.startsWith("\"") && raw.endsWith("\"")) {
            return raw.substring(1, raw.length() - 1);
        }
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException ignored) {
        }
        try {
            return Double.parseDouble(raw);
        } catch (NumberFormatException ignored) {
        }
        return raw;
    }

    @SuppressWarnings("unchecked")
    private boolean compare(Object left, String operator, Object right) {
        if ("==".equals(operator)) {
            return toComparable(left).compareTo(toComparable(right)) == 0;
        }
        if ("!=".equals(operator)) {
            return toComparable(left).compareTo(toComparable(right)) != 0;
        }

        Comparable<Object> leftComparable = toComparable(left);
        Comparable<Object> rightComparable = toComparable(right);
        int result = leftComparable.compareTo(rightComparable);

        return switch (operator) {
            case ">" -> result > 0;
            case "<" -> result < 0;
            case ">=" -> result >= 0;
            case "<=" -> result <= 0;
            default -> throw new IllegalArgumentException("Unknown operator: " + operator);
        };
    }

    @SuppressWarnings("unchecked")
    private Comparable<Object> toComparable(Object value) {
        if (value instanceof Number number) {
            return (Comparable<Object>) (Comparable<?>) number.doubleValue();
        }
        if (value instanceof Comparable) {
            return (Comparable<Object>) value;
        }
        return (Comparable<Object>) (Comparable<?>) value.toString();
    }
}
