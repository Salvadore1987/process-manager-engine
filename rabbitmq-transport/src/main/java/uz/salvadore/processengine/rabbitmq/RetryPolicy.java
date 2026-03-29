package uz.salvadore.processengine.rabbitmq;

import java.time.Duration;

/**
 * Calculates exponential backoff delay for retry attempts.
 * Formula: baseInterval × 2^attempt, capped at maxInterval.
 */
public final class RetryPolicy {

    private final Duration baseInterval;
    private final Duration maxInterval;
    private final int maxAttempts;

    public RetryPolicy(Duration baseInterval, Duration maxInterval, int maxAttempts) {
        this.baseInterval = baseInterval;
        this.maxInterval = maxInterval;
        this.maxAttempts = maxAttempts;
    }

    public Duration getDelay(int attempt) {
        if (attempt < 0) {
            throw new IllegalArgumentException("Attempt must be non-negative");
        }
        long baseMs = baseInterval.toMillis();
        long delayMs = baseMs * (1L << Math.min(attempt, 30));
        long maxMs = maxInterval.toMillis();
        return Duration.ofMillis(Math.min(delayMs, maxMs));
    }

    public boolean shouldRetry(int attempt) {
        return attempt < maxAttempts;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public Duration getBaseInterval() {
        return baseInterval;
    }

    public Duration getMaxInterval() {
        return maxInterval;
    }
}
