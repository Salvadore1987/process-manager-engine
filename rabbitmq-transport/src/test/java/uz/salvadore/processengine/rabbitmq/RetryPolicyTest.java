package uz.salvadore.processengine.rabbitmq;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("RetryPolicy")
class RetryPolicyTest {

    private static final Duration BASE_INTERVAL = Duration.ofSeconds(5);
    private static final Duration MAX_INTERVAL = Duration.ofMinutes(5);
    private static final int MAX_ATTEMPTS = 3;

    private final RetryPolicy retryPolicy = new RetryPolicy(BASE_INTERVAL, MAX_INTERVAL, MAX_ATTEMPTS);

    @Nested
    @DisplayName("getDelay")
    class GetDelay {

        @Test
        @DisplayName("attempt 0 returns base interval (5s)")
        void attempt0ReturnsBaseInterval() {
            // Arrange
            int attempt = 0;

            // Act
            Duration delay = retryPolicy.getDelay(attempt);

            // Assert
            assertThat(delay).isEqualTo(Duration.ofSeconds(5));
        }

        @Test
        @DisplayName("attempt 1 returns 2x base interval (10s)")
        void attempt1ReturnsTwiceBaseInterval() {
            // Arrange
            int attempt = 1;

            // Act
            Duration delay = retryPolicy.getDelay(attempt);

            // Assert
            assertThat(delay).isEqualTo(Duration.ofSeconds(10));
        }

        @Test
        @DisplayName("attempt 2 returns 4x base interval (20s)")
        void attempt2ReturnsFourTimesBaseInterval() {
            // Arrange
            int attempt = 2;

            // Act
            Duration delay = retryPolicy.getDelay(attempt);

            // Assert
            assertThat(delay).isEqualTo(Duration.ofSeconds(20));
        }

        @ParameterizedTest(name = "attempt {0} -> {1}s")
        @CsvSource({
                "0, 5",
                "1, 10",
                "2, 20",
                "3, 40",
                "4, 80",
                "5, 160"
        })
        @DisplayName("exponential backoff formula: baseInterval * 2^attempt")
        void exponentialBackoffFormula(int attempt, long expectedSeconds) {
            // Act
            Duration delay = retryPolicy.getDelay(attempt);

            // Assert
            assertThat(delay).isEqualTo(Duration.ofSeconds(expectedSeconds));
        }

        @Test
        @DisplayName("delay never exceeds maxInterval")
        void delayNeverExceedsMaxInterval() {
            // Arrange — attempt 10 would give 5s * 1024 = 5120s, but max is 300s
            int highAttempt = 10;

            // Act
            Duration delay = retryPolicy.getDelay(highAttempt);

            // Assert
            assertThat(delay).isEqualTo(MAX_INTERVAL);
        }

        @Test
        @DisplayName("delay at boundary exactly equals maxInterval when formula exceeds cap")
        void delayCapsAtMaxInterval() {
            // Arrange — attempt 6 would give 5s * 64 = 320s > 300s
            int attempt = 6;

            // Act
            Duration delay = retryPolicy.getDelay(attempt);

            // Assert
            assertThat(delay).isEqualTo(Duration.ofMinutes(5));
        }

        @Test
        @DisplayName("negative attempt throws IllegalArgumentException")
        void negativeAttemptThrowsException() {
            // Arrange
            int negativeAttempt = -1;

            // Act & Assert
            assertThatThrownBy(() -> retryPolicy.getDelay(negativeAttempt))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("non-negative");
        }

        @ParameterizedTest
        @ValueSource(ints = {-1, -5, -100})
        @DisplayName("any negative attempt value throws IllegalArgumentException")
        void anyNegativeAttemptThrowsException(int negativeAttempt) {
            // Act & Assert
            assertThatThrownBy(() -> retryPolicy.getDelay(negativeAttempt))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("very high attempt does not overflow, caps at maxInterval")
        void veryHighAttemptDoesNotOverflow() {
            // Arrange — attempt 30 is the max shift; attempt 31+ also capped
            int extremeAttempt = 31;

            // Act
            Duration delay = retryPolicy.getDelay(extremeAttempt);

            // Assert
            assertThat(delay).isEqualTo(MAX_INTERVAL);
        }
    }

    @Nested
    @DisplayName("shouldRetry")
    class ShouldRetry {

        @Test
        @DisplayName("returns true when attempt is less than maxAttempts")
        void returnsTrueForAttemptBelowMax() {
            // Arrange & Act & Assert
            assertThat(retryPolicy.shouldRetry(0)).isTrue();
            assertThat(retryPolicy.shouldRetry(1)).isTrue();
            assertThat(retryPolicy.shouldRetry(2)).isTrue();
        }

        @Test
        @DisplayName("returns false when attempt equals maxAttempts")
        void returnsFalseWhenAttemptEqualsMax() {
            // Arrange
            int attempt = MAX_ATTEMPTS;

            // Act
            boolean result = retryPolicy.shouldRetry(attempt);

            // Assert
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("returns false when attempt exceeds maxAttempts")
        void returnsFalseWhenAttemptExceedsMax() {
            // Arrange
            int attempt = MAX_ATTEMPTS + 1;

            // Act
            boolean result = retryPolicy.shouldRetry(attempt);

            // Assert
            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("accessors")
    class Accessors {

        @Test
        @DisplayName("getMaxAttempts returns configured value")
        void getMaxAttempts() {
            assertThat(retryPolicy.getMaxAttempts()).isEqualTo(MAX_ATTEMPTS);
        }

        @Test
        @DisplayName("getBaseInterval returns configured value")
        void getBaseInterval() {
            assertThat(retryPolicy.getBaseInterval()).isEqualTo(BASE_INTERVAL);
        }

        @Test
        @DisplayName("getMaxInterval returns configured value")
        void getMaxInterval() {
            assertThat(retryPolicy.getMaxInterval()).isEqualTo(MAX_INTERVAL);
        }
    }
}
