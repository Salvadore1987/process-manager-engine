package uz.salvadore.processengine.worker.autoconfigure;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import uz.salvadore.processengine.worker.listener.TaskListenerContainer;

/**
 * Health indicator that reports the status of the worker's RabbitMQ connection
 * and active consumers.
 */
public class WorkerHealthIndicator implements HealthIndicator {

    private final TaskListenerContainer listenerContainer;

    public WorkerHealthIndicator(TaskListenerContainer listenerContainer) {
        this.listenerContainer = listenerContainer;
    }

    @Override
    public Health health() {
        boolean connectionAlive = listenerContainer.isConnectionAlive();
        int activeConsumers = listenerContainer.getActiveConsumerCount();
        boolean isRunning = listenerContainer.isRunning();

        Health.Builder builder = connectionAlive && isRunning
                ? Health.up()
                : Health.down();

        return builder
                .withDetail("running", isRunning)
                .withDetail("connectionAlive", connectionAlive)
                .withDetail("activeConsumers", activeConsumers)
                .build();
    }
}
