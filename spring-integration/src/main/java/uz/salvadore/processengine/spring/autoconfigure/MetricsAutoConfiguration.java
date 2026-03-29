package uz.salvadore.processengine.spring.autoconfigure;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import uz.salvadore.processengine.spring.metrics.ProcessEngineMetrics;

@AutoConfiguration(after = ProcessEngineAutoConfiguration.class)
@ConditionalOnClass(MeterRegistry.class)
public class MetricsAutoConfiguration {

    @Bean
    @ConditionalOnBean(MeterRegistry.class)
    @ConditionalOnMissingBean
    public ProcessEngineMetrics processEngineMetrics(MeterRegistry meterRegistry) {
        return new ProcessEngineMetrics(meterRegistry);
    }
}
