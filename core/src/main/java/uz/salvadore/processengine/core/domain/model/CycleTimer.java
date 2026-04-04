package uz.salvadore.processengine.core.domain.model;

import java.time.Duration;

public record CycleTimer(
        int repetitions,
        Duration interval
) {

    /**
     * Parses ISO 8601 repeating interval: R3/PT10H, R/PT5M
     */
    public static CycleTimer parse(String value) {
        if (value == null || !value.startsWith("R")) {
            throw new IllegalArgumentException("Invalid cycle timer format: " + value);
        }

        int slashIndex = value.indexOf('/');
        if (slashIndex < 0) {
            throw new IllegalArgumentException("Invalid cycle timer format, missing '/': " + value);
        }

        String repetitionPart = value.substring(1, slashIndex);
        String durationPart = value.substring(slashIndex + 1);

        int reps = repetitionPart.isEmpty() ? -1 : Integer.parseInt(repetitionPart);
        Duration duration = Duration.parse(durationPart);

        return new CycleTimer(reps, duration);
    }

    public boolean isInfinite() {
        return repetitions == -1;
    }
}
