package uz.salvadore.processengine.worker.autoconfigure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.rabbitmq.client.ConnectionFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.support.ResourcePatternResolver;
import uz.salvadore.processengine.worker.listener.TaskListenerContainer;
import uz.salvadore.processengine.worker.registry.TaskHandlerBeanPostProcessor;
import uz.salvadore.processengine.worker.registry.TaskHandlerRegistry;

/**
 * Auto-configuration for the process engine worker starter.
 * <p>
 * Creates all infrastructure beans required to consume task messages from RabbitMQ
 * and dispatch them to {@code @TaskHandler}-annotated methods.
 * </p>
 */
@AutoConfiguration
@EnableConfigurationProperties(WorkerProperties.class)
public class WorkerAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ConnectionFactory workerRabbitConnectionFactory(WorkerProperties properties) {
        WorkerProperties.RabbitMqProperties rmq = properties.getRabbitmq();
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(rmq.getHost());
        factory.setPort(rmq.getPort());
        factory.setUsername(rmq.getUsername());
        factory.setPassword(rmq.getPassword());
        factory.setVirtualHost(rmq.getVirtualHost());
        return factory;
    }

    @Bean
    @ConditionalOnMissingBean
    public ObjectMapper workerObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }

    @Bean
    @ConditionalOnMissingBean
    public TaskHandlerRegistry taskHandlerRegistry() {
        return new TaskHandlerRegistry();
    }

    @Bean
    public TaskHandlerBeanPostProcessor taskHandlerBeanPostProcessor(TaskHandlerRegistry registry) {
        return new TaskHandlerBeanPostProcessor(registry);
    }

    @Bean
    @ConditionalOnMissingBean
    public TaskListenerContainer taskListenerContainer(ConnectionFactory workerRabbitConnectionFactory,
                                                       TaskHandlerRegistry registry,
                                                       ObjectMapper workerObjectMapper) {
        return new TaskListenerContainer(workerRabbitConnectionFactory, registry, workerObjectMapper);
    }

    @Bean
    @ConditionalOnMissingBean(name = "workerHealthIndicator")
    public WorkerHealthIndicator workerHealthIndicator(TaskListenerContainer listenerContainer) {
        return new WorkerHealthIndicator(listenerContainer);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "process-engine.worker", name = "engine-url")
    public ProcessEngineClient processEngineClient(WorkerProperties properties,
                                                    ObjectMapper workerObjectMapper) {
        return new ProcessEngineClient(properties.getEngineUrl(), properties.getAuth(), workerObjectMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "process-engine.worker", name = "engine-url")
    public BpmnAutoDeployer bpmnAutoDeployer(ProcessEngineClient processEngineClient,
                                              WorkerProperties properties,
                                              ResourcePatternResolver resourcePatternResolver) {
        return new BpmnAutoDeployer(processEngineClient, properties, resourcePatternResolver);
    }
}
