package uz.salvadore.processengine.core.engine.condition;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SimpleConditionEvaluatorTest {

    private final SimpleConditionEvaluator evaluator = new SimpleConditionEvaluator();

    @Nested
    @DisplayName("Equality operator (==)")
    class EqualityTests {

        @Test
        void shouldReturnTrueWhenLongValuesAreEqual() {
            // Arrange
            Map<String, Object> variables = Map.of("count", 5L);

            // Act
            boolean result = evaluator.evaluate("${count == 5}", variables);

            // Assert
            assertThat(result).isTrue();
        }

        @Test
        void shouldReturnFalseWhenLongValuesAreNotEqual() {
            // Arrange
            Map<String, Object> variables = Map.of("count", 3L);

            // Act
            boolean result = evaluator.evaluate("${count == 5}", variables);

            // Assert
            assertThat(result).isFalse();
        }

        @Test
        void shouldReturnTrueWhenStringValuesAreEqual() {
            // Arrange
            Map<String, Object> variables = Map.of("status", "approved");

            // Act
            boolean result = evaluator.evaluate("${status == 'approved'}", variables);

            // Assert
            assertThat(result).isTrue();
        }

        @Test
        void shouldReturnFalseWhenStringValuesAreNotEqual() {
            // Arrange
            Map<String, Object> variables = Map.of("status", "rejected");

            // Act
            boolean result = evaluator.evaluate("${status == 'approved'}", variables);

            // Assert
            assertThat(result).isFalse();
        }

        @Test
        void shouldReturnTrueWhenBooleanValuesAreEqual() {
            // Arrange
            Map<String, Object> variables = Map.of("active", true);

            // Act
            boolean result = evaluator.evaluate("${active == true}", variables);

            // Assert
            assertThat(result).isTrue();
        }

        @Test
        void shouldSupportDoubleQuotedStrings() {
            // Arrange
            Map<String, Object> variables = Map.of("status", "approved");

            // Act
            boolean result = evaluator.evaluate("${status == \"approved\"}", variables);

            // Assert
            assertThat(result).isTrue();
        }
    }

    @Nested
    @DisplayName("Inequality operator (!=)")
    class InequalityTests {

        @Test
        void shouldReturnTrueWhenValuesAreNotEqual() {
            // Arrange
            Map<String, Object> variables = Map.of("status", "pending");

            // Act
            boolean result = evaluator.evaluate("${status != 'approved'}", variables);

            // Assert
            assertThat(result).isTrue();
        }

        @Test
        void shouldReturnFalseWhenValuesAreEqual() {
            // Arrange
            Map<String, Object> variables = Map.of("status", "approved");

            // Act
            boolean result = evaluator.evaluate("${status != 'approved'}", variables);

            // Assert
            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("Greater than operator (>)")
    class GreaterThanTests {

        @Test
        void shouldReturnTrueWhenLeftIsGreater() {
            // Arrange
            Map<String, Object> variables = Map.of("amount", 15000L);

            // Act
            boolean result = evaluator.evaluate("${amount > 10000}", variables);

            // Assert
            assertThat(result).isTrue();
        }

        @Test
        void shouldReturnFalseWhenLeftIsEqual() {
            // Arrange
            Map<String, Object> variables = Map.of("amount", 10000L);

            // Act
            boolean result = evaluator.evaluate("${amount > 10000}", variables);

            // Assert
            assertThat(result).isFalse();
        }

        @Test
        void shouldReturnFalseWhenLeftIsLess() {
            // Arrange
            Map<String, Object> variables = Map.of("amount", 5000L);

            // Act
            boolean result = evaluator.evaluate("${amount > 10000}", variables);

            // Assert
            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("Less than operator (<)")
    class LessThanTests {

        @Test
        void shouldReturnTrueWhenLeftIsLess() {
            // Arrange
            Map<String, Object> variables = Map.of("count", 3L);

            // Act
            boolean result = evaluator.evaluate("${count < 5}", variables);

            // Assert
            assertThat(result).isTrue();
        }

        @Test
        void shouldReturnFalseWhenLeftIsEqual() {
            // Arrange
            Map<String, Object> variables = Map.of("count", 5L);

            // Act
            boolean result = evaluator.evaluate("${count < 5}", variables);

            // Assert
            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("Greater than or equal operator (>=)")
    class GreaterThanOrEqualTests {

        @Test
        void shouldReturnTrueWhenLeftIsGreater() {
            // Arrange
            Map<String, Object> variables = Map.of("amount", 15000L);

            // Act
            boolean result = evaluator.evaluate("${amount >= 10000}", variables);

            // Assert
            assertThat(result).isTrue();
        }

        @Test
        void shouldReturnTrueWhenLeftIsEqual() {
            // Arrange
            Map<String, Object> variables = Map.of("amount", 10000L);

            // Act
            boolean result = evaluator.evaluate("${amount >= 10000}", variables);

            // Assert
            assertThat(result).isTrue();
        }

        @Test
        void shouldReturnFalseWhenLeftIsLess() {
            // Arrange
            Map<String, Object> variables = Map.of("amount", 9999L);

            // Act
            boolean result = evaluator.evaluate("${amount >= 10000}", variables);

            // Assert
            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("Less than or equal operator (<=)")
    class LessThanOrEqualTests {

        @Test
        void shouldReturnTrueWhenLeftIsLess() {
            // Arrange
            Map<String, Object> variables = Map.of("count", 3L);

            // Act
            boolean result = evaluator.evaluate("${count <= 5}", variables);

            // Assert
            assertThat(result).isTrue();
        }

        @Test
        void shouldReturnTrueWhenLeftIsEqual() {
            // Arrange
            Map<String, Object> variables = Map.of("count", 5L);

            // Act
            boolean result = evaluator.evaluate("${count <= 5}", variables);

            // Assert
            assertThat(result).isTrue();
        }

        @Test
        void shouldReturnFalseWhenLeftIsGreater() {
            // Arrange
            Map<String, Object> variables = Map.of("count", 6L);

            // Act
            boolean result = evaluator.evaluate("${count <= 5}", variables);

            // Assert
            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("Type handling")
    class TypeHandlingTests {

        @Test
        void shouldCompareIntegerVariableWithLongLiteral() {
            // Arrange
            Map<String, Object> variables = Map.of("amount", 15000);

            // Act
            boolean result = evaluator.evaluate("${amount > 10000}", variables);

            // Assert
            assertThat(result).isTrue();
        }

        @Test
        void shouldCompareDoubleValues() {
            // Arrange
            Map<String, Object> variables = Map.of("price", 99.99);

            // Act
            boolean result = evaluator.evaluate("${price > 50.0}", variables);

            // Assert
            assertThat(result).isTrue();
        }

        @Test
        void shouldHandleBooleanFalseComparison() {
            // Arrange
            Map<String, Object> variables = Map.of("active", false);

            // Act
            boolean result = evaluator.evaluate("${active == false}", variables);

            // Assert
            assertThat(result).isTrue();
        }
    }

    @Nested
    @DisplayName("Whitespace handling")
    class WhitespaceTests {

        @Test
        void shouldHandleExtraWhitespace() {
            // Arrange
            Map<String, Object> variables = Map.of("amount", 15000L);

            // Act
            boolean result = evaluator.evaluate("${ amount  >  10000 }", variables);

            // Assert
            assertThat(result).isTrue();
        }

        @Test
        void shouldHandleLeadingAndTrailingWhitespace() {
            // Arrange
            Map<String, Object> variables = Map.of("amount", 15000L);

            // Act
            boolean result = evaluator.evaluate("  ${amount > 10000}  ", variables);

            // Assert
            assertThat(result).isTrue();
        }
    }

    @Nested
    @DisplayName("Error handling")
    class ErrorHandlingTests {

        @Test
        void shouldThrowOnInvalidExpression() {
            // Arrange
            Map<String, Object> variables = Map.of("amount", 100L);

            // Act & Assert
            assertThatThrownBy(() -> evaluator.evaluate("invalid expression", variables))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid condition expression");
        }

        @Test
        void shouldThrowOnMissingVariable() {
            // Arrange
            Map<String, Object> variables = Map.of("other", 100L);

            // Act & Assert
            assertThatThrownBy(() -> evaluator.evaluate("${amount > 10000}", variables))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Variable 'amount' not found");
        }

        @Test
        void shouldThrowOnEmptyExpression() {
            // Arrange
            Map<String, Object> variables = Map.of("amount", 100L);

            // Act & Assert
            assertThatThrownBy(() -> evaluator.evaluate("", variables))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void shouldThrowOnMalformedDollarBrace() {
            // Arrange
            Map<String, Object> variables = Map.of("amount", 100L);

            // Act & Assert
            assertThatThrownBy(() -> evaluator.evaluate("${}", variables))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("Boolean expressions")
    class BooleanExpressionTests {

        @Test
        void shouldReturnTrueForTrueBooleanVariable() {
            // Arrange
            Map<String, Object> variables = Map.of("isPaymentSuccess", true);

            // Act
            boolean result = evaluator.evaluate("${isPaymentSuccess}", variables);

            // Assert
            assertThat(result).isTrue();
        }

        @Test
        void shouldReturnFalseForFalseBooleanVariable() {
            // Arrange
            Map<String, Object> variables = Map.of("isPaymentSuccess", false);

            // Act
            boolean result = evaluator.evaluate("${isPaymentSuccess}", variables);

            // Assert
            assertThat(result).isFalse();
        }

        @Test
        void shouldNegateWhenExclamationMarkUsedWithTrueValue() {
            // Arrange
            Map<String, Object> variables = Map.of("isPaymentSuccess", true);

            // Act
            boolean result = evaluator.evaluate("${!isPaymentSuccess}", variables);

            // Assert
            assertThat(result).isFalse();
        }

        @Test
        void shouldNegateWhenExclamationMarkUsedWithFalseValue() {
            // Arrange
            Map<String, Object> variables = Map.of("isPaymentSuccess", false);

            // Act
            boolean result = evaluator.evaluate("${!isPaymentSuccess}", variables);

            // Assert
            assertThat(result).isTrue();
        }

        @Test
        void shouldThrowWhenBooleanVariableNotFound() {
            // Arrange
            Map<String, Object> variables = Map.of("other", true);

            // Act & Assert
            assertThatThrownBy(() -> evaluator.evaluate("${nonExistent}", variables))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("not found");
        }

        @Test
        void shouldThrowWhenVariableIsNotBoolean() {
            // Arrange
            Map<String, Object> variables = Map.of("stringVar", "hello");

            // Act & Assert
            assertThatThrownBy(() -> evaluator.evaluate("${stringVar}", variables))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("not boolean");
        }

        @Test
        void shouldHandleWhitespaceInBooleanExpression() {
            // Arrange
            Map<String, Object> variables = Map.of("active", true);

            // Act
            boolean result = evaluator.evaluate("${ active }", variables);

            // Assert
            assertThat(result).isTrue();
        }

        @Test
        void shouldHandleWhitespaceInNegatedBooleanExpression() {
            // Arrange
            Map<String, Object> variables = Map.of("active", true);

            // Act
            boolean result = evaluator.evaluate("${ ! active }", variables);

            // Assert
            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("Parameterized comparisons")
    class ParameterizedTests {

        @ParameterizedTest
        @CsvSource({
                "15000, >, 10000, true",
                "10000, >, 10000, false",
                "5000, >, 10000, false",
                "5000, <, 10000, true",
                "10000, <, 10000, false",
                "10000, ==, 10000, true",
                "9999, ==, 10000, false",
                "10000, >=, 10000, true",
                "10001, >=, 10000, true",
                "9999, >=, 10000, false",
                "10000, <=, 10000, true",
                "9999, <=, 10000, true",
                "10001, <=, 10000, false",
                "5000, !=, 10000, true",
                "10000, !=, 10000, false"
        })
        void shouldEvaluateNumericComparisonsCorrectly(long variableValue, String operator,
                                                       long literalValue, boolean expected) {
            // Arrange
            Map<String, Object> variables = Map.of("amount", variableValue);
            String expression = "${amount " + operator + " " + literalValue + "}";

            // Act
            boolean result = evaluator.evaluate(expression, variables);

            // Assert
            assertThat(result).isEqualTo(expected);
        }
    }
}
