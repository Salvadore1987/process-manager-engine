package uz.salvadore.processengine.spring.health;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import uz.salvadore.processengine.rabbitmq.RabbitMqConnectionManager;

/**
 * Health indicator for the RabbitMQ transport connection.
 * Reports UP when the connection is open.
 */
public class RabbitMqTransportHealthIndicator implements HealthIndicator {

    private final RabbitMqConnectionManager connectionManager;

    public RabbitMqTransportHealthIndicator(RabbitMqConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    @Override
    public Health health() {
        try {
            if (connectionManager.isConnected()) {
                return Health.up()
                        .withDetail("status", "connected")
                        .build();
            }
            return Health.down()
                    .withDetail("status", "disconnected")
                    .build();
        } catch (Exception e) {
            return Health.down()
                    .withDetail("status", "error")
                    .withException(e)
                    .build();
        }
    }
}
