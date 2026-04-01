package uz.salvadore.processengine.worker.registry;

/**
 * Retry configuration for a specific worker topic, extracted from {@link uz.salvadore.processengine.worker.annotation.JobWorker}.
 *
 * @param enabled   whether retry is enabled
 * @param maxAttempts maximum number of retry attempts
 * @param backoffMs backoff interval between retries in milliseconds
 */
public record WorkerRetryConfig(boolean enabled, int maxAttempts, long backoffMs) {

    public static final WorkerRetryConfig DISABLED = new WorkerRetryConfig(false, 0, 0);
}
