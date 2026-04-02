package uz.salvadore.processengine.spring.autoconfigure;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import uz.salvadore.processengine.core.adapter.inmemory.InMemoryEventStore;
import uz.salvadore.processengine.core.port.outgoing.ProcessEventStore;

@AutoConfiguration(before = ProcessEngineAutoConfiguration.class)
public class EventStoreAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(ProcessEventStore.class)
    public ProcessEventStore inMemoryEventStore() {
        return new InMemoryEventStore();
    }
}
