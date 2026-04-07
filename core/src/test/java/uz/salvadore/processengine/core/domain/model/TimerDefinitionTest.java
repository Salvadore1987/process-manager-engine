package uz.salvadore.processengine.core.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TimerDefinitionTest {

    @Nested
    @DisplayName("asDuration()")
    class AsDurationTests {

        @ParameterizedTest
        @CsvSource({
                "PT5S, 5",
                "PT10M, 600",
                "PT1H, 3600"
        })
        @DisplayName("Should parse ISO 8601 duration values")
        void shouldParseDurationValues(String iso8601, long expectedSeconds) {
            // Arrange
            TimerDefinition timerDefinition = new TimerDefinition(TimerDefinition.TimerType.DURATION, iso8601);

            // Act
            Duration duration = timerDefinition.asDuration();

            // Assert
            assertThat(duration).isEqualTo(Duration.ofSeconds(expectedSeconds));
        }

        @Test
        @DisplayName("Should parse P1D (1 day) duration")
        void shouldParseOneDayDuration() {
            // Arrange
            TimerDefinition timerDefinition = new TimerDefinition(TimerDefinition.TimerType.DURATION, "P1D");

            // Act
            Duration duration = timerDefinition.asDuration();

            // Assert
            assertThat(duration).isEqualTo(Duration.ofDays(1));
        }

        @Test
        @DisplayName("Should throw IllegalStateException when type is DATE")
        void shouldThrowWhenTypeIsDate() {
            // Arrange
            TimerDefinition timerDefinition = new TimerDefinition(
                    TimerDefinition.TimerType.DATE, "2025-01-01T00:00:00Z");

            // Act & Assert
            assertThatThrownBy(timerDefinition::asDuration)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Not a duration timer");
        }

        @Test
        @DisplayName("Should throw IllegalStateException when type is CYCLE")
        void shouldThrowWhenTypeIsCycle() {
            // Arrange
            TimerDefinition timerDefinition = new TimerDefinition(
                    TimerDefinition.TimerType.CYCLE, "R3/PT10H");

            // Act & Assert
            assertThatThrownBy(timerDefinition::asDuration)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Not a duration timer");
        }
    }

    @Nested
    @DisplayName("asDate()")
    class AsDateTests {

        @Test
        @DisplayName("Should parse valid ISO 8601 date")
        void shouldParseValidDate() {
            // Arrange
            TimerDefinition timerDefinition = new TimerDefinition(
                    TimerDefinition.TimerType.DATE, "2025-06-15T10:30:00Z");

            // Act
            Instant date = timerDefinition.asDate();

            // Assert
            assertThat(date).isEqualTo(Instant.parse("2025-06-15T10:30:00Z"));
        }

        @Test
        @DisplayName("Should throw IllegalStateException when type is DURATION")
        void shouldThrowWhenTypeIsDuration() {
            // Arrange
            TimerDefinition timerDefinition = new TimerDefinition(
                    TimerDefinition.TimerType.DURATION, "PT5S");

            // Act & Assert
            assertThatThrownBy(timerDefinition::asDate)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Not a date timer");
        }

        @Test
        @DisplayName("Should throw IllegalStateException when type is CYCLE")
        void shouldThrowWhenTypeIsCycle() {
            // Arrange
            TimerDefinition timerDefinition = new TimerDefinition(
                    TimerDefinition.TimerType.CYCLE, "R3/PT10H");

            // Act & Assert
            assertThatThrownBy(timerDefinition::asDate)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Not a date timer");
        }
    }

    @Nested
    @DisplayName("asCycle()")
    class AsCycleTests {

        @Test
        @DisplayName("Should parse R3/PT10H as 3 repetitions with 10-hour interval")
        void shouldParseFiniteCycle() {
            // Arrange
            TimerDefinition timerDefinition = new TimerDefinition(
                    TimerDefinition.TimerType.CYCLE, "R3/PT10H");

            // Act
            CycleTimer cycleTimer = timerDefinition.asCycle();

            // Assert
            assertThat(cycleTimer.repetitions()).isEqualTo(3);
            assertThat(cycleTimer.interval()).isEqualTo(Duration.ofHours(10));
            assertThat(cycleTimer.isInfinite()).isFalse();
        }

        @Test
        @DisplayName("Should parse R/PT5M as infinite repetitions with 5-minute interval")
        void shouldParseInfiniteCycle() {
            // Arrange
            TimerDefinition timerDefinition = new TimerDefinition(
                    TimerDefinition.TimerType.CYCLE, "R/PT5M");

            // Act
            CycleTimer cycleTimer = timerDefinition.asCycle();

            // Assert
            assertThat(cycleTimer.repetitions()).isEqualTo(-1);
            assertThat(cycleTimer.interval()).isEqualTo(Duration.ofMinutes(5));
            assertThat(cycleTimer.isInfinite()).isTrue();
        }

        @Test
        @DisplayName("Should throw IllegalStateException when type is DURATION")
        void shouldThrowWhenTypeIsDuration() {
            // Arrange
            TimerDefinition timerDefinition = new TimerDefinition(
                    TimerDefinition.TimerType.DURATION, "PT5S");

            // Act & Assert
            assertThatThrownBy(timerDefinition::asCycle)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Not a cycle timer");
        }

        @Test
        @DisplayName("Should throw IllegalStateException when type is DATE")
        void shouldThrowWhenTypeIsDate() {
            // Arrange
            TimerDefinition timerDefinition = new TimerDefinition(
                    TimerDefinition.TimerType.DATE, "2025-01-01T00:00:00Z");

            // Act & Assert
            assertThatThrownBy(timerDefinition::asCycle)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Not a cycle timer");
        }
    }

    @Nested
    @DisplayName("CycleTimer.parse()")
    class CycleTimerParseTests {

        @Test
        @DisplayName("Should parse R3/PT10H correctly")
        void shouldParseFiniteCycleTimer() {
            // Arrange & Act
            CycleTimer cycleTimer = CycleTimer.parse("R3/PT10H");

            // Assert
            assertThat(cycleTimer.repetitions()).isEqualTo(3);
            assertThat(cycleTimer.interval()).isEqualTo(Duration.ofHours(10));
        }

        @Test
        @DisplayName("Should parse R/PT5M as infinite cycle")
        void shouldParseInfiniteCycleTimer() {
            // Arrange & Act
            CycleTimer cycleTimer = CycleTimer.parse("R/PT5M");

            // Assert
            assertThat(cycleTimer.repetitions()).isEqualTo(-1);
            assertThat(cycleTimer.interval()).isEqualTo(Duration.ofMinutes(5));
        }

        @Test
        @DisplayName("Should parse R1/PT30S as single repetition")
        void shouldParseSingleRepetition() {
            // Arrange & Act
            CycleTimer cycleTimer = CycleTimer.parse("R1/PT30S");

            // Assert
            assertThat(cycleTimer.repetitions()).isEqualTo(1);
            assertThat(cycleTimer.interval()).isEqualTo(Duration.ofSeconds(30));
        }

        @Test
        @DisplayName("Should throw on null input")
        void shouldThrowOnNullInput() {
            // Act & Assert
            assertThatThrownBy(() -> CycleTimer.parse(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid cycle timer format");
        }

        @Test
        @DisplayName("Should throw on input not starting with R")
        void shouldThrowOnInvalidPrefix() {
            // Act & Assert
            assertThatThrownBy(() -> CycleTimer.parse("PT5M"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid cycle timer format");
        }

        @Test
        @DisplayName("Should throw on input missing slash separator")
        void shouldThrowOnMissingSeparator() {
            // Act & Assert
            assertThatThrownBy(() -> CycleTimer.parse("R3PT10H"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("missing '/'");
        }
    }

    @Nested
    @DisplayName("CycleTimer.isInfinite()")
    class CycleTimerIsInfiniteTests {

        @Test
        @DisplayName("Should return true when repetitions is -1")
        void shouldReturnTrueForInfinite() {
            // Arrange
            CycleTimer cycleTimer = new CycleTimer(-1, Duration.ofMinutes(5));

            // Act & Assert
            assertThat(cycleTimer.isInfinite()).isTrue();
        }

        @Test
        @DisplayName("Should return false when repetitions is positive")
        void shouldReturnFalseForFinite() {
            // Arrange
            CycleTimer cycleTimer = new CycleTimer(3, Duration.ofHours(1));

            // Act & Assert
            assertThat(cycleTimer.isInfinite()).isFalse();
        }

        @Test
        @DisplayName("Should return false when repetitions is zero")
        void shouldReturnFalseForZero() {
            // Arrange
            CycleTimer cycleTimer = new CycleTimer(0, Duration.ofHours(1));

            // Act & Assert
            assertThat(cycleTimer.isInfinite()).isFalse();
        }
    }
}
