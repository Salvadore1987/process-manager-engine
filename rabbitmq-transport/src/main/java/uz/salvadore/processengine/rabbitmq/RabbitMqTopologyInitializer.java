package uz.salvadore.processengine.rabbitmq;

import com.rabbitmq.client.BuiltinExchangeType;
import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uz.salvadore.processengine.rabbitmq.config.RabbitMqTransportConfig;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeoutException;

/**
 * Declares RabbitMQ exchanges and shared queues for the process engine topology.
 * Exchanges: tasks (topic), retry (topic), dlq (fanout), timers (topic).
 * Shared queues: task.execute, task.result, task.retry — created once at startup.
 */
public final class RabbitMqTopologyInitializer {

    private static final Logger log = LoggerFactory.getLogger(RabbitMqTopologyInitializer.class);

    public static final String EXECUTE_QUEUE = "task.execute";
    public static final String RESULT_QUEUE = "task.result";
    public static final String RETRY_QUEUE = "task.retry";

    private final RabbitMqConnectionManager connectionManager;
    private final RabbitMqTransportConfig config;

    public RabbitMqTopologyInitializer(RabbitMqConnectionManager connectionManager,
                                       RabbitMqTransportConfig config) {
        this.connectionManager = connectionManager;
        this.config = config;
    }

    /**
     * Declares the core exchanges, the DLQ queue, and the shared task queues. Call once at startup.
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

            // Shared execute queue
            channel.queueDeclare(EXECUTE_QUEUE, true, false, false, Map.of(
                    "x-dead-letter-exchange", config.getDlqExchange()
            ));
            channel.queueBind(EXECUTE_QUEUE, config.getTasksExchange(), EXECUTE_QUEUE);
            log.info("Declared and bound shared execute queue: {}", EXECUTE_QUEUE);

            // Shared result queue
            channel.queueDeclare(RESULT_QUEUE, true, false, false, null);
            channel.queueBind(RESULT_QUEUE, config.getTasksExchange(), RESULT_QUEUE);
            log.info("Declared and bound shared result queue: {}", RESULT_QUEUE);

            // Shared retry queue — dead-letters back to execute queue via tasks exchange
            channel.queueDeclare(RETRY_QUEUE, true, false, false, Map.of(
                    "x-dead-letter-exchange", config.getTasksExchange(),
                    "x-dead-letter-routing-key", EXECUTE_QUEUE
            ));
            channel.queueBind(RETRY_QUEUE, config.getRetryExchange(), RETRY_QUEUE);
            log.info("Declared and bound shared retry queue: {}", RETRY_QUEUE);
        }
    }
}
