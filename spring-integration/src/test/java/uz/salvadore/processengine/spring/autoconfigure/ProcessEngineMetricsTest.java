package uz.salvadore.processengine.spring.autoconfigure;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import uz.salvadore.processengine.spring.metrics.ProcessEngineMetrics;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ProcessEngineMetrics")
class ProcessEngineMetricsTest {

    private MeterRegistry meterRegistry;
    private ProcessEngineMetrics metrics;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        metrics = new ProcessEngineMetrics(meterRegistry);
    }

    @Nested
    @DisplayName("instances.started counter")
    class InstancesStartedCounter {

        @Test
        @DisplayName("starts at zero")
        void startsAtZero() {
            // Arrange - done in setUp

            // Act
            double count = meterRegistry.counter("process.engine.instances.started").count();

            // Assert
            assertThat(count).isZero();
        }

        @Test
        @DisplayName("increments on recordInstanceStarted")
        void incrementsOnRecordInstanceStarted() {
            // Arrange - done in setUp

            // Act
            metrics.recordInstanceStarted();
            metrics.recordInstanceStarted();

            // Assert
            double count = meterRegistry.counter("process.engine.instances.started").count();
            assertThat(count).isEqualTo(2.0);
        }
    }

    @Nested
    @DisplayName("instances.completed counter")
    class InstancesCompletedCounter {

        @Test
        @DisplayName("starts at zero")
        void startsAtZero() {
            // Arrange - done in setUp

            // Act
            double count = meterRegistry.counter("process.engine.instances.completed").count();

            // Assert
            assertThat(count).isZero();
        }

        @Test
        @DisplayName("increments on recordInstanceCompleted")
        void incrementsOnRecordInstanceCompleted() {
            // Arrange - done in setUp

            // Act
            metrics.recordInstanceCompleted();
            metrics.recordInstanceCompleted();
            metrics.recordInstanceCompleted();

            // Assert
            double count = meterRegistry.counter("process.engine.instances.completed").count();
            assertThat(count).isEqualTo(3.0);
        }
    }

    @Nested
    @DisplayName("instances.errors counter")
    class InstancesErrorsCounter {

        @Test
        @DisplayName("starts at zero")
        void startsAtZero() {
            // Arrange - done in setUp

            // Act
            double count = meterRegistry.counter("process.engine.instances.errors").count();

            // Assert
            assertThat(count).isZero();
        }

        @Test
        @DisplayName("increments on recordInstanceError")
        void incrementsOnRecordInstanceError() {
            // Arrange - done in setUp

            // Act
            metrics.recordInstanceError();

            // Assert
            double count = meterRegistry.counter("process.engine.instances.errors").count();
            assertThat(count).isEqualTo(1.0);
        }
    }

    @Nested
    @DisplayName("task.duration timer")
    class TaskDurationTimer {

        @Test
        @DisplayName("records duration for a given topic")
        void recordsDurationForTopic() {
            // Arrange
            Duration duration = Duration.ofMillis(250);

            // Act
            metrics.recordTaskDuration("payment.process", duration);

            // Assert
            io.micrometer.core.instrument.Timer timer = meterRegistry
                    .find("process.engine.task.duration")
                    .tag("topic", "payment.process")
                    .timer();
            assertThat(timer).isNotNull();
            assertThat(timer.count()).isEqualTo(1);
            assertThat(timer.totalTime(java.util.concurrent.TimeUnit.MILLISECONDS)).isEqualTo(250.0);
        }

        @Test
        @DisplayName("records multiple durations for different topics")
        void recordsMultipleDurationsForDifferentTopics() {
            // Arrange
            Duration duration1 = Duration.ofMillis(100);
            Duration duration2 = Duration.ofMillis(200);

            // Act
            metrics.recordTaskDuration("topic.a", duration1);
            metrics.recordTaskDuration("topic.b", duration2);

            // Assert
            io.micrometer.core.instrument.Timer timerA = meterRegistry
                    .find("process.engine.task.duration")
                    .tag("topic", "topic.a")
                    .timer();
            io.micrometer.core.instrument.Timer timerB = meterRegistry
                    .find("process.engine.task.duration")
                    .tag("topic", "topic.b")
                    .timer();

            assertThat(timerA).isNotNull();
            assertThat(timerA.count()).isEqualTo(1);
            assertThat(timerB).isNotNull();
            assertThat(timerB.count()).isEqualTo(1);
        }

        @Test
        @DisplayName("accumulates durations for the same topic")
        void accumulatesDurationsForSameTopic() {
            // Arrange
            Duration first = Duration.ofMillis(100);
            Duration second = Duration.ofMillis(300);

            // Act
            metrics.recordTaskDuration("same.topic", first);
            metrics.recordTaskDuration("same.topic", second);

            // Assert
            io.micrometer.core.instrument.Timer timer = meterRegistry
                    .find("process.engine.task.duration")
                    .tag("topic", "same.topic")
                    .timer();
            assertThat(timer).isNotNull();
            assertThat(timer.count()).isEqualTo(2);
            assertThat(timer.totalTime(java.util.concurrent.TimeUnit.MILLISECONDS)).isEqualTo(400.0);
        }
    }

    @Nested
    @DisplayName("counter accessors")
    class CounterAccessors {

        @Test
        @DisplayName("getInstancesStarted returns the started counter")
        void getInstancesStartedReturnsCounter() {
            // Arrange - done in setUp

            // Act
            metrics.recordInstanceStarted();

            // Assert
            assertThat(metrics.getInstancesStarted().count()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("getInstancesCompleted returns the completed counter")
        void getInstancesCompletedReturnsCounter() {
            // Arrange - done in setUp

            // Act
            metrics.recordInstanceCompleted();

            // Assert
            assertThat(metrics.getInstancesCompleted().count()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("getInstancesErrors returns the errors counter")
        void getInstancesErrorsReturnsCounter() {
            // Arrange - done in setUp

            // Act
            metrics.recordInstanceError();

            // Assert
            assertThat(metrics.getInstancesErrors().count()).isEqualTo(1.0);
        }
    }
}
