package uz.salvadore.processengine.redis;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import uz.salvadore.processengine.core.util.UUIDv7;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RedisSequenceGeneratorTest extends AbstractRedisTest {

    private RedisSequenceGenerator sequenceGenerator;

    @BeforeEach
    void setUp() {
        sequenceGenerator = new RedisSequenceGenerator(redisTemplate);
    }

    @Nested
    @DisplayName("next()")
    class Next {

        @Test
        @DisplayName("should return 1 on first call and 2 on second call")
        void shouldReturnIncrementingValues() {
            // Arrange
            UUID processInstanceId = UUIDv7.generate();

            // Act
            long first = sequenceGenerator.next(processInstanceId);
            long second = sequenceGenerator.next(processInstanceId);

            // Assert
            assertThat(first).isEqualTo(1L);
            assertThat(second).isEqualTo(2L);
        }

        @Test
        @DisplayName("should maintain independent counters for different processInstanceIds")
        void shouldHaveIndependentCounters() {
            // Arrange
            UUID processA = UUIDv7.generate();
            UUID processB = UUIDv7.generate();

            // Act
            long firstA = sequenceGenerator.next(processA);
            long firstB = sequenceGenerator.next(processB);
            long secondA = sequenceGenerator.next(processA);

            // Assert
            assertThat(firstA).isEqualTo(1L);
            assertThat(firstB).isEqualTo(1L);
            assertThat(secondA).isEqualTo(2L);
        }
    }

    @Nested
    @DisplayName("current()")
    class Current {

        @Test
        @DisplayName("should return 0 for unknown processInstanceId")
        void shouldReturnZeroForUnknownId() {
            // Arrange
            UUID unknownId = UUIDv7.generate();

            // Act
            long current = sequenceGenerator.current(unknownId);

            // Assert
            assertThat(current).isZero();
        }

        @Test
        @DisplayName("should return correct value after next() calls")
        void shouldReturnCorrectValueAfterNextCalls() {
            // Arrange
            UUID processInstanceId = UUIDv7.generate();
            sequenceGenerator.next(processInstanceId);
            sequenceGenerator.next(processInstanceId);
            sequenceGenerator.next(processInstanceId);

            // Act
            long current = sequenceGenerator.current(processInstanceId);

            // Assert
            assertThat(current).isEqualTo(3L);
        }
    }

    @Nested
    @DisplayName("reset()")
    class Reset {

        @Test
        @DisplayName("should reset counter back to 0")
        void shouldResetCounterToZero() {
            // Arrange
            UUID processInstanceId = UUIDv7.generate();
            sequenceGenerator.next(processInstanceId);
            sequenceGenerator.next(processInstanceId);

            // Act
            sequenceGenerator.reset(processInstanceId);

            // Assert
            assertThat(sequenceGenerator.current(processInstanceId)).isZero();
        }
    }
}
