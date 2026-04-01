package uz.salvadore.processengine.spring.autoconfigure;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import uz.salvadore.processengine.core.adapter.inmemory.InMemoryEventStore;
import uz.salvadore.processengine.core.port.outgoing.ProcessEventStore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@DisplayName("EventStoreAutoConfiguration")
class EventStoreAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(EventStoreAutoConfiguration.class));

    @Test
    @DisplayName("creates InMemoryEventStore as default fallback")
    void createsInMemoryEventStoreAsDefault() {
        // Arrange & Act & Assert
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(ProcessEventStore.class);
            assertThat(context.getBean(ProcessEventStore.class)).isInstanceOf(InMemoryEventStore.class);
        });
    }

    @Test
    @DisplayName("does not override user-provided ProcessEventStore bean")
    void doesNotOverrideUserProvidedEventStore() {
        // Arrange
        ProcessEventStore customStore = mock(ProcessEventStore.class);

        // Act & Assert
        contextRunner
                .withBean(ProcessEventStore.class, () -> customStore)
                .run(context -> {
                    assertThat(context).hasSingleBean(ProcessEventStore.class);
                    assertThat(context.getBean(ProcessEventStore.class)).isSameAs(customStore);
                });
    }
}
