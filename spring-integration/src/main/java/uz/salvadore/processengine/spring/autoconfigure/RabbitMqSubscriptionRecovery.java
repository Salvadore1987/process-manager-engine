package uz.salvadore.processengine.spring.autoconfigure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import uz.salvadore.processengine.core.domain.model.ProcessDefinition;
import uz.salvadore.processengine.core.domain.model.ServiceTask;
import uz.salvadore.processengine.core.port.outgoing.MessageTransport;
import uz.salvadore.processengine.core.port.outgoing.ProcessDefinitionStore;
import uz.salvadore.processengine.rabbitmq.RabbitMqTopologyInitializer;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

/**
 * Restores RabbitMQ result queue subscriptions on startup for definitions
 * that already exist in the persistent store (e.g. Redis).
 * Without this, after a restart no consumers listen for worker responses.
 */
public class RabbitMqSubscriptionRecovery implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(RabbitMqSubscriptionRecovery.class);

    private final ProcessDefinitionStore definitionStore;
    private final RabbitMqTopologyInitializer topologyInitializer;
    private final MessageTransport messageTransport;
    private final Consumer<MessageTransport.MessageResult> resultCallback;
    private final Set<String> subscribedTopics = ConcurrentHashMap.newKeySet();

    private volatile boolean running = false;

    public RabbitMqSubscriptionRecovery(ProcessDefinitionStore definitionStore,
                                        RabbitMqTopologyInitializer topologyInitializer,
                                        MessageTransport messageTransport,
                                        Consumer<MessageTransport.MessageResult> resultCallback) {
        this.definitionStore = definitionStore;
        this.topologyInitializer = topologyInitializer;
        this.messageTransport = messageTransport;
        this.resultCallback = resultCallback;
    }

    @Override
    public void start() {
        List<ProcessDefinition> definitions = definitionStore.list();
        if (definitions.isEmpty()) {
            log.info("No existing process definitions found, skipping subscription recovery");
            running = true;
            return;
        }

        log.info("Recovering RabbitMQ subscriptions for {} existing definition(s)", definitions.size());

        for (ProcessDefinition definition : definitions) {
            definition.getFlowNodes().stream()
                    .filter(ServiceTask.class::isInstance)
                    .map(ServiceTask.class::cast)
                    .map(ServiceTask::topic)
                    .distinct()
                    .forEach(topic -> {
                        if (subscribedTopics.add(topic)) {
                            try {
                                topologyInitializer.ensureTopicQueues(topic);
                                messageTransport.subscribe(topic, resultCallback);
                                log.info("Recovered subscription for topic '{}' (definition: {})",
                                        topic, definition.getKey());
                            } catch (IOException | TimeoutException e) {
                                log.error("Failed to recover subscription for topic '{}': {}",
                                        topic, e.getMessage(), e);
                            }
                        }
                    });
        }

        running = true;
        log.info("Subscription recovery complete, {} topic(s) subscribed", subscribedTopics.size());
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
        return Integer.MIN_VALUE + 200;
    }
}
