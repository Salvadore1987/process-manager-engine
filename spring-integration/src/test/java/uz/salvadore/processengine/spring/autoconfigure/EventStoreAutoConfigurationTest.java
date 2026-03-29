package uz.salvadore.processengine.spring.autoconfigure;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import uz.salvadore.processengine.core.adapter.inmemory.InMemoryEventStore;
import uz.salvadore.processengine.core.adapter.noop.NoOpEventStore;
import uz.salvadore.processengine.core.port.outgoing.ProcessEventStore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@DisplayName("EventStoreAutoConfiguration")
class EventStoreAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(EventStoreAutoConfiguration.class));

    @Test
    @DisplayName("creates NoOpEventStore when persistence.enabled is not set")
    void createsNoOpEventStoreWhenPersistenceNotSet() {
        // Arrange & Act & Assert
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(ProcessEventStore.class);
            assertThat(context.getBean(ProcessEventStore.class)).isInstanceOf(NoOpEventStore.class);
        });
    }

    @Test
    @DisplayName("creates InMemoryEventStore when persistence.enabled is true")
    void createsInMemoryEventStoreWhenPersistenceEnabled() {
        // Arrange
        ApplicationContextRunner runner = contextRunner
                .withPropertyValues("process-engine.persistence.enabled=true");

        // Act & Assert
        runner.run(context -> {
            assertThat(context).hasSingleBean(ProcessEventStore.class);
            assertThat(context.getBean(ProcessEventStore.class)).isInstanceOf(InMemoryEventStore.class);
        });
    }

    @Test
    @DisplayName("creates NoOpEventStore when persistence.enabled is false")
    void createsNoOpEventStoreWhenPersistenceDisabled() {
        // Arrange
        ApplicationContextRunner runner = contextRunner
                .withPropertyValues("process-engine.persistence.enabled=false");

        // Act & Assert
        runner.run(context -> {
            assertThat(context).hasSingleBean(ProcessEventStore.class);
            assertThat(context.getBean(ProcessEventStore.class)).isInstanceOf(NoOpEventStore.class);
        });
    }

    @Test
    @DisplayName("does not override user-provided ProcessEventStore bean")
    void doesNotOverrideUserProvidedEventStore() {
        // Arrange
        ProcessEventStore customStore = mock(ProcessEventStore.class);

        // Act & Assert
        contextRunner
                .withPropertyValues("process-engine.persistence.enabled=true")
                .withBean(ProcessEventStore.class, () -> customStore)
                .run(context -> {
                    assertThat(context).hasSingleBean(ProcessEventStore.class);
                    assertThat(context.getBean(ProcessEventStore.class)).isSameAs(customStore);
                });
    }
}
