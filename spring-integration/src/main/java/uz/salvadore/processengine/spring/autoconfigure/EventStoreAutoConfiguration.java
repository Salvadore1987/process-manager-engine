package uz.salvadore.processengine.spring.autoconfigure;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import uz.salvadore.processengine.core.adapter.inmemory.InMemoryEventStore;
import uz.salvadore.processengine.core.adapter.noop.NoOpEventStore;
import uz.salvadore.processengine.core.port.outgoing.ProcessEventStore;

@AutoConfiguration(before = ProcessEngineAutoConfiguration.class)
public class EventStoreAutoConfiguration {

    @Bean
    @ConditionalOnProperty(name = "process-engine.persistence.enabled", havingValue = "true")
    @ConditionalOnMissingBean(ProcessEventStore.class)
    public ProcessEventStore inMemoryEventStore() {
        return new InMemoryEventStore();
    }

    @Bean
    @ConditionalOnMissingBean(ProcessEventStore.class)
    public ProcessEventStore noOpEventStore() {
        return new NoOpEventStore();
    }
}
