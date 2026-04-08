package uz.salvadore.processengine.spring.autoconfigure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.annotation.Bean;
import uz.salvadore.processengine.core.engine.ProcessEngine;
import uz.salvadore.processengine.core.port.outgoing.MessageTransport;
import uz.salvadore.processengine.core.port.outgoing.TimerService;
import uz.salvadore.processengine.rabbitmq.RabbitMqConnectionManager;
import uz.salvadore.processengine.rabbitmq.RabbitMqMessageTransport;
import uz.salvadore.processengine.rabbitmq.RabbitMqTimerService;
import uz.salvadore.processengine.rabbitmq.RabbitMqTopologyInitializer;
import uz.salvadore.processengine.rabbitmq.config.RabbitMqTransportConfig;

import java.util.function.Consumer;

@AutoConfiguration(before = ProcessEngineAutoConfiguration.class)
@ConditionalOnClass(name = "com.rabbitmq.client.ConnectionFactory")
public class RabbitMqTransportAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public RabbitMqTransportConfig rabbitMqTransportConfig(ProcessEngineProperties properties) {
        ProcessEngineProperties.RabbitMq rmq = properties.getRabbitmq();
        ProcessEngineProperties.Retry retry = properties.getRetry();
        return RabbitMqTransportConfig.builder()
                .host(rmq.getHost())
                .port(rmq.getPort())
                .username(rmq.getUsername())
                .password(rmq.getPassword())
                .virtualHost(rmq.getVirtualHost())
                .tasksExchange(rmq.getExchanges().getTasks())
                .retryExchange(rmq.getExchanges().getRetry())
                .dlqExchange(rmq.getExchanges().getDlq())
                .timersExchange(rmq.getExchanges().getTimers())
                .maxRetryAttempts(retry.getMaxAttempts())
                .retryBaseInterval(retry.getBaseInterval())
                .retryMaxInterval(retry.getMaxInterval())
                .build();
    }

    @Bean
    @ConditionalOnMissingBean
    public RabbitMqConnectionManager rabbitMqConnectionManager(RabbitMqTransportConfig config) {
        return new RabbitMqConnectionManager(config);
    }

    @Bean
    @ConditionalOnMissingBean
    public RabbitMqTopologyInitializer rabbitMqTopologyInitializer(
            RabbitMqConnectionManager connectionManager,
            RabbitMqTransportConfig config) {
        return new RabbitMqTopologyInitializer(connectionManager, config);
    }

    @Bean
    @ConditionalOnMissingBean(MessageTransport.class)
    public RabbitMqMessageTransport rabbitMqMessageTransport(
            RabbitMqConnectionManager connectionManager,
            RabbitMqTransportConfig config) {
        return new RabbitMqMessageTransport(connectionManager, config);
    }

    @Bean
    @ConditionalOnMissingBean(TimerService.class)
    public RabbitMqTimerService rabbitMqTimerService(
            RabbitMqConnectionManager connectionManager,
            RabbitMqTransportConfig config) {
        return new RabbitMqTimerService(connectionManager, config);
    }

    @Bean
    @ConditionalOnMissingBean(name = "rabbitMqTopologyLifecycle")
    public SmartLifecycle rabbitMqTopologyLifecycle(RabbitMqTopologyInitializer topologyInitializer) {
        return new RabbitMqTopologyLifecycle(topologyInitializer);
    }

    @Bean
    @ConditionalOnMissingBean(name = "rabbitMqTimerLifecycle")
    public SmartLifecycle rabbitMqTimerLifecycle(
            RabbitMqTimerService rabbitMqTimerService,
            ObjectProvider<ProcessEngine> processEngineProvider) {
        Consumer<TimerService.TimerCallback> timerCallback = callback -> {
            ProcessEngine engine = processEngineProvider.getIfAvailable();
            if (engine == null) {
                log.warn("Timer fired during shutdown, ignoring: processInstanceId={}, tokenId={}",
                        callback.processInstanceId(), callback.tokenId());
                return;
            }
            try {
                engine.completeTimer(callback.processInstanceId(), callback.tokenId(), callback.nodeId());
            } catch (IllegalStateException e) {
                log.warn("Timer fired for unknown/completed instance, ignoring: processInstanceId={}, nodeId={} — {}",
                        callback.processInstanceId(), callback.nodeId(), e.getMessage());
            }
        };
        return new RabbitMqTimerLifecycle(rabbitMqTimerService, timerCallback);
    }

    private static final Logger log = LoggerFactory.getLogger(RabbitMqTransportAutoConfiguration.class);

    static class RabbitMqTopologyLifecycle implements SmartLifecycle {

        private static final Logger log = LoggerFactory.getLogger(RabbitMqTopologyLifecycle.class);

        private final RabbitMqTopologyInitializer topologyInitializer;
        private volatile boolean running = false;

        RabbitMqTopologyLifecycle(RabbitMqTopologyInitializer topologyInitializer) {
            this.topologyInitializer = topologyInitializer;
        }

        @Override
        public void start() {
            try {
                topologyInitializer.initializeTopology();
                running = true;
                log.info("RabbitMQ topology initialized successfully");
            } catch (Exception e) {
                throw new IllegalStateException("Failed to initialize RabbitMQ topology", e);
            }
        }

        @Override
        public void stop() {
            running = false;
        }

        @Override
        public boolean isRunning() {
            return running;
        }

        @Override
        public int getPhase() {
            return Integer.MIN_VALUE + 100;
        }
    }

    static class RabbitMqTimerLifecycle implements SmartLifecycle {

        private static final Logger log = LoggerFactory.getLogger(RabbitMqTimerLifecycle.class);

        private final RabbitMqTimerService timerService;
        private final Consumer<TimerService.TimerCallback> timerResultCallback;
        private volatile boolean running = false;

        RabbitMqTimerLifecycle(RabbitMqTimerService timerService,
                               Consumer<TimerService.TimerCallback> timerResultCallback) {
            this.timerService = timerService;
            this.timerResultCallback = timerResultCallback;
        }

        @Override
        public void start() {
            try {
                timerService.initialize(timerResultCallback);
                running = true;
                log.info("RabbitMQ timer consumer initialized successfully");
            } catch (Exception e) {
                throw new IllegalStateException("Failed to initialize RabbitMQ timer consumer", e);
            }
        }

        @Override
        public void stop() {
            timerService.close();
            running = false;
        }

        @Override
        public boolean isRunning() {
            return running;
        }

        @Override
        public int getPhase() {
            return Integer.MIN_VALUE + 200;
        }
    }
}
