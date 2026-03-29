package uz.salvadore.processengine.spring.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.time.Duration;

/**
 * Micrometer metrics for the process engine.
 * Counters: instances.started, instances.completed, instances.errors
 * Timer: task.duration (tagged by topic)
 */
public class ProcessEngineMetrics {

    private final Counter instancesStarted;
    private final Counter instancesCompleted;
    private final Counter instancesErrors;
    private final MeterRegistry meterRegistry;

    public ProcessEngineMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;

        this.instancesStarted = Counter.builder("process.engine.instances.started")
                .description("Number of process instances started")
                .register(meterRegistry);

        this.instancesCompleted = Counter.builder("process.engine.instances.completed")
                .description("Number of process instances completed successfully")
                .register(meterRegistry);

        this.instancesErrors = Counter.builder("process.engine.instances.errors")
                .description("Number of process instances that ended with error")
                .register(meterRegistry);
    }

    public void recordInstanceStarted() {
        instancesStarted.increment();
    }

    public void recordInstanceCompleted() {
        instancesCompleted.increment();
    }

    public void recordInstanceError() {
        instancesErrors.increment();
    }

    public void recordTaskDuration(String topic, Duration duration) {
        Timer.builder("process.engine.task.duration")
                .description("Duration of task execution")
                .tag("topic", topic)
                .register(meterRegistry)
                .record(duration);
    }

    public Counter getInstancesStarted() { return instancesStarted; }
    public Counter getInstancesCompleted() { return instancesCompleted; }
    public Counter getInstancesErrors() { return instancesErrors; }
}
