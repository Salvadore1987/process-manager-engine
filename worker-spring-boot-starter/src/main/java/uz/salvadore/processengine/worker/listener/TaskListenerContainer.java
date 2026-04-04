package uz.salvadore.processengine.worker.listener;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DeliverCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import uz.salvadore.processengine.worker.ExternalTaskHandler;
import uz.salvadore.processengine.worker.TaskContext;
import uz.salvadore.processengine.worker.registry.TaskHandlerRegistry;

import java.io.IOException;
import uz.salvadore.processengine.worker.registry.WorkerRetryConfig;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A {@link SmartLifecycle} component that creates a single RabbitMQ consumer
 * on the shared execute queue and dispatches incoming messages to the
 * corresponding handler based on the x-task-topic header.
 */
public class TaskListenerContainer implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(TaskListenerContainer.class);

    private static final String EXCHANGE_NAME = "process-engine.tasks";
    private static final String EXECUTE_QUEUE = "task.execute";
    private static final String RESULT_QUEUE = "task.result";
    private static final String CORRELATION_ID_HEADER = "x-correlation-id";
    private static final String TOPIC_HEADER = "x-task-topic";

    private final ConnectionFactory connectionFactory;
    private final TaskHandlerRegistry registry;
    private final ObjectMapper objectMapper;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final Set<String> consumerTags = ConcurrentHashMap.newKeySet();

    private volatile Connection connection;
    private volatile Channel channel;

    public TaskListenerContainer(ConnectionFactory connectionFactory,
                                 TaskHandlerRegistry registry,
                                 ObjectMapper objectMapper) {
        this.connectionFactory = connectionFactory;
        this.registry = registry;
        this.objectMapper = objectMapper;
    }

    @Override
    public void start() {
        if (running.compareAndSet(false, true)) {
            try {
                connection = connectionFactory.newConnection();
                channel = connection.createChannel();
                channel.basicQos(1);

                channel.queueDeclarePassive(EXECUTE_QUEUE);

                DeliverCallback deliverCallback = (consumerTag, delivery) -> {
                    long deliveryTag = delivery.getEnvelope().getDeliveryTag();
                    try {
                        String topic = extractTopic(delivery.getProperties());
                        handleDelivery(topic, delivery.getProperties(), delivery.getBody());
                        channel.basicAck(deliveryTag, false);
                    } catch (Exception ex) {
                        log.error("Error processing message: {}", ex.getMessage(), ex);
                        channel.basicNack(deliveryTag, false, false);
                    }
                };

                String consumerTag = channel.basicConsume(EXECUTE_QUEUE, false, deliverCallback,
                        cancelCallback -> log.warn("Shared task consumer was cancelled"));
                consumerTags.add(consumerTag);
                log.info("Started shared consumer on queue '{}'", EXECUTE_QUEUE);
            } catch (IOException | TimeoutException ex) {
                running.set(false);
                throw new IllegalStateException("Failed to start task listener container", ex);
            }
        }
    }

    @Override
    public void stop() {
        if (running.compareAndSet(true, false)) {
            try {
                if (channel != null && channel.isOpen()) {
                    for (String consumerTag : consumerTags) {
                        try {
                            channel.basicCancel(consumerTag);
                        } catch (IOException ex) {
                            log.warn("Failed to cancel consumer {}: {}", consumerTag, ex.getMessage());
                        }
                    }
                    consumerTags.clear();
                    channel.close();
                }
                if (connection != null && connection.isOpen()) {
                    connection.close();
                }
            } catch (IOException | TimeoutException ex) {
                log.warn("Error during shutdown: {}", ex.getMessage(), ex);
            }
        }
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    @Override
    public int getPhase() {
        return Integer.MAX_VALUE;
    }

    public boolean isConnectionAlive() {
        return connection != null && connection.isOpen();
    }

    public int getActiveConsumerCount() {
        return consumerTags.size();
    }

    private String extractTopic(AMQP.BasicProperties properties) {
        Map<String, Object> headers = properties.getHeaders();
        if (headers != null && headers.containsKey(TOPIC_HEADER)) {
            return headers.get(TOPIC_HEADER).toString();
        }
        throw new IllegalStateException("No x-task-topic header found in message");
    }

    private void handleDelivery(String topic, AMQP.BasicProperties properties, byte[] body)
            throws Exception {

        ExternalTaskHandler handler = registry.getHandler(topic);
        if (handler == null) {
            throw new IllegalStateException("No handler registered for topic: " + topic);
        }

        Map<String, Object> messageBody = objectMapper.readValue(body,
                new TypeReference<Map<String, Object>>() {
                });

        String correlationId = resolveCorrelationId(properties);
        WorkerRetryConfig retryConfig = registry.getRetryConfig(topic);

        if (retryConfig.enabled()) {
            executeWithRetry(handler, topic, correlationId, messageBody, retryConfig);
        } else {
            executeOnce(handler, topic, correlationId, messageBody);
        }
    }

    private void executeOnce(ExternalTaskHandler handler, String topic,
                             String correlationId, Map<String, Object> variables) throws Exception {
        TaskContext.ResponseSender responseSender = new RabbitMqResponseSender(topic);
        TaskContext context = new TaskContext(correlationId, variables, responseSender);

        handler.execute(context);

        if (!context.isResponded()) {
            log.warn("Handler for topic '{}' did not call complete() or error(). "
                    + "Auto-completing with empty result.", topic);
            context.complete(Map.of());
        }
    }

    private void executeWithRetry(ExternalTaskHandler handler, String topic,
                                  String correlationId, Map<String, Object> variables,
                                  WorkerRetryConfig retryConfig) throws Exception {
        int attempt = 0;
        while (true) {
            try {
                executeOnce(handler, topic, correlationId, variables);
                return;
            } catch (Exception ex) {
                attempt++;
                if (attempt > retryConfig.maxAttempts()) {
                    log.error("Handler for topic '{}' failed after {} retries, giving up. correlationId={}",
                            topic, retryConfig.maxAttempts(), correlationId, ex);
                    throw ex;
                }
                log.warn("Handler for topic '{}' failed (attempt {}/{}), retrying in {}ms. correlationId={}",
                        topic, attempt, retryConfig.maxAttempts(), retryConfig.backoffMs(), correlationId, ex);
                Thread.sleep(retryConfig.backoffMs());
            }
        }
    }

    private class RabbitMqResponseSender implements TaskContext.ResponseSender {

        private final String topic;

        RabbitMqResponseSender(String topic) {
            this.topic = topic;
        }

        @Override
        public void sendSuccess(String correlationId, Map<String, Object> result) {
            try {
                byte[] responseBody = objectMapper.writeValueAsBytes(result);
                AMQP.BasicProperties responseProperties = buildResponseProperties(correlationId, topic);
                channel.basicPublish(EXCHANGE_NAME, RESULT_QUEUE, responseProperties, responseBody);
                log.debug("Published success response for topic '{}', correlationId={}", topic, correlationId);
            } catch (IOException ex) {
                throw new RuntimeException("Failed to publish success response for topic: " + topic, ex);
            }
        }

        @Override
        public void sendError(String correlationId, String errorCode, String errorMessage) {
            try {
                Map<String, Object> errorPayload = new HashMap<>();
                errorPayload.put("__error", true);
                errorPayload.put("__errorCode", errorCode);
                errorPayload.put("message", errorMessage);

                byte[] responseBody = objectMapper.writeValueAsBytes(errorPayload);
                AMQP.BasicProperties responseProperties = buildResponseProperties(correlationId, topic);
                channel.basicPublish(EXCHANGE_NAME, RESULT_QUEUE, responseProperties, responseBody);
                log.debug("Published error response for topic '{}', correlationId={}", topic, correlationId);
            } catch (IOException ex) {
                throw new RuntimeException("Failed to publish error response for topic: " + topic, ex);
            }
        }
    }

    private AMQP.BasicProperties buildResponseProperties(String correlationId, String topic) {
        Map<String, Object> headers = new HashMap<>();
        headers.put(CORRELATION_ID_HEADER, correlationId);
        headers.put(TOPIC_HEADER, topic);

        return new AMQP.BasicProperties.Builder()
                .correlationId(correlationId)
                .contentType("application/json")
                .deliveryMode(2)
                .headers(headers)
                .build();
    }

    private String resolveCorrelationId(AMQP.BasicProperties properties) {
        if (properties.getCorrelationId() != null) {
            return properties.getCorrelationId();
        }
        if (properties.getHeaders() != null && properties.getHeaders().containsKey(CORRELATION_ID_HEADER)) {
            Object headerValue = properties.getHeaders().get(CORRELATION_ID_HEADER);
            return headerValue != null ? headerValue.toString() : null;
        }
        return null;
    }
}
