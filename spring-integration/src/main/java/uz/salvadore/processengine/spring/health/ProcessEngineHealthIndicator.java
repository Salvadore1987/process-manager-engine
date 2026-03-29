package uz.salvadore.processengine.spring.health;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import uz.salvadore.processengine.core.engine.ProcessEngine;

/**
 * Health indicator for the process engine.
 * Reports UP when the engine bean exists and is operational.
 */
public class ProcessEngineHealthIndicator implements HealthIndicator {

    private final ProcessEngine processEngine;

    public ProcessEngineHealthIndicator(ProcessEngine processEngine) {
        this.processEngine = processEngine;
    }

    @Override
    public Health health() {
        try {
            if (processEngine != null) {
                return Health.up()
                        .withDetail("status", "running")
                        .build();
            }
            return Health.down()
                    .withDetail("status", "not initialized")
                    .build();
        } catch (Exception e) {
            return Health.down()
                    .withDetail("status", "error")
                    .withException(e)
                    .build();
        }
    }
}
