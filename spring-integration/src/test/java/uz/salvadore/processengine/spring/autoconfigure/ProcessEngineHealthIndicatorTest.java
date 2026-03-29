package uz.salvadore.processengine.spring.autoconfigure;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;
import uz.salvadore.processengine.core.engine.ProcessEngine;
import uz.salvadore.processengine.spring.health.ProcessEngineHealthIndicator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@DisplayName("ProcessEngineHealthIndicator")
class ProcessEngineHealthIndicatorTest {

    @Test
    @DisplayName("reports UP with status=running when ProcessEngine is available")
    void reportsUpWhenProcessEngineAvailable() {
        // Arrange
        ProcessEngine processEngine = mock(ProcessEngine.class);
        ProcessEngineHealthIndicator healthIndicator = new ProcessEngineHealthIndicator(processEngine);

        // Act
        Health health = healthIndicator.health();

        // Assert
        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("status", "running");
    }

    @Test
    @DisplayName("reports DOWN with status=not initialized when ProcessEngine is null")
    void reportsDownWhenProcessEngineIsNull() {
        // Arrange
        ProcessEngineHealthIndicator healthIndicator = new ProcessEngineHealthIndicator(null);

        // Act
        Health health = healthIndicator.health();

        // Assert
        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("status", "not initialized");
    }
}
