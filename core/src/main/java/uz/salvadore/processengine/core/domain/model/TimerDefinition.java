package uz.salvadore.processengine.core.domain.model;

import java.time.Duration;
import java.time.Instant;

public record TimerDefinition(
        TimerType type,
        String value
) {

    public enum TimerType {
        DURATION,
        DATE,
        CYCLE
    }

    public Duration asDuration() {
        if (type != TimerType.DURATION) {
            throw new IllegalStateException("Not a duration timer, type is " + type);
        }
        return Duration.parse(value);
    }

    public Instant asDate() {
        if (type != TimerType.DATE) {
            throw new IllegalStateException("Not a date timer, type is " + type);
        }
        return Instant.parse(value);
    }

    public CycleTimer asCycle() {
        if (type != TimerType.CYCLE) {
            throw new IllegalStateException("Not a cycle timer, type is " + type);
        }
        return CycleTimer.parse(value);
    }
}
