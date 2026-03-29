package uz.salvadore.processengine.rabbitmq;

import com.rabbitmq.client.BuiltinExchangeType;
import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uz.salvadore.processengine.rabbitmq.config.RabbitMqTransportConfig;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;

/**
 * Declares RabbitMQ exchanges and queues for the process engine topology.
 * Exchanges: tasks (topic), retry (topic), dlq (fanout), timers (headers with x-delayed-message plugin or topic fallback).
 * Per-topic queues are declared lazily on first use.
 */
public final class RabbitMqTopologyInitializer {

    private static final Logger log = LoggerFactory.getLogger(RabbitMqTopologyInitializer.class);

    private final RabbitMqConnectionManager connectionManager;
    private final RabbitMqTransportConfig config;
    private final Set<String> declaredTopics = ConcurrentHashMap.newKeySet();

    public RabbitMqTopologyInitializer(RabbitMqConnectionManager connectionManager,
                                       RabbitMqTransportConfig config) {
        this.connectionManager = connectionManager;
        this.config = config;
    }

    /**
     * Declares the core exchanges and the DLQ queue. Call once at startup.
     */
    public void initializeTopology() throws IOException, TimeoutException {
        try (Channel channel = connectionManager.createChannel()) {
            channel.exchangeDeclare(config.getTasksExchange(), BuiltinExchangeType.TOPIC, true);
            log.info("Declared tasks exchange: {}", config.getTasksExchange());

            channel.exchangeDeclare(config.getRetryExchange(), BuiltinExchangeType.TOPIC, true);
            log.info("Declared retry exchange: {}", config.getRetryExchange());

            channel.exchangeDeclare(config.getDlqExchange(), BuiltinExchangeType.FANOUT, true);
            log.info("Declared DLQ exchange: {}", config.getDlqExchange());

            channel.exchangeDeclare(config.getTimersExchange(), BuiltinExchangeType.TOPIC, true);
            log.info("Declared timers exchange: {}", config.getTimersExchange());

            String dlqQueue = "process-engine.dlq";
            channel.queueDeclare(dlqQueue, true, false, false, null);
            channel.queueBind(dlqQueue, config.getDlqExchange(), "");
            log.info("Declared and bound DLQ queue: {}", dlqQueue);
        }
    }

    /**
     * Lazily declares per-topic queues: task.{topic}.execute and task.{topic}.result.
     * Also declares retry queue with TTL dead-lettering back to tasks exchange.
     */
    public void ensureTopicQueues(String topic) throws IOException, TimeoutException {
        if (declaredTopics.contains(topic)) {
            return;
        }

        try (Channel channel = connectionManager.createChannel()) {
            String executeQueue = "task." + topic + ".execute";
            String resultQueue = "task." + topic + ".result";
            String retryQueue = "task." + topic + ".retry";

            channel.queueDeclare(executeQueue, true, false, false, Map.of(
                    "x-dead-letter-exchange", config.getDlqExchange()
            ));
            channel.queueBind(executeQueue, config.getTasksExchange(), "task." + topic + ".execute");

            channel.queueDeclare(resultQueue, true, false, false, null);
            channel.queueBind(resultQueue, config.getTasksExchange(), "task." + topic + ".result");

            channel.queueDeclare(retryQueue, true, false, false, Map.of(
                    "x-dead-letter-exchange", config.getTasksExchange(),
                    "x-dead-letter-routing-key", "task." + topic + ".execute"
            ));
            channel.queueBind(retryQueue, config.getRetryExchange(), "task." + topic + ".retry");

            declaredTopics.add(topic);
            log.info("Declared queues for topic '{}': {}, {}, {}", topic, executeQueue, resultQueue, retryQueue);
        }
    }

    public Set<String> getDeclaredTopics() {
        return Set.copyOf(declaredTopics);
    }
}
