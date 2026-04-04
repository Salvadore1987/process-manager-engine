package uz.salvadore.processengine.rabbitmq;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uz.salvadore.processengine.core.port.outgoing.MessageTransport;
import uz.salvadore.processengine.rabbitmq.config.RabbitMqTransportConfig;
import uz.salvadore.processengine.rabbitmq.correlation.CorrelationIdResolver;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * RabbitMQ implementation of MessageTransport.
 * Sends task execution messages to a shared execute queue and subscribes
 * to a shared result queue, dispatching by the x-task-topic header.
 */
public final class RabbitMqMessageTransport implements MessageTransport {

    private static final Logger log = LoggerFactory.getLogger(RabbitMqMessageTransport.class);

    private final RabbitMqConnectionManager connectionManager;
    private final RabbitMqTransportConfig config;
    private final CorrelationIdResolver correlationIdResolver;
    private final ObjectMapper objectMapper;
    private final ConcurrentHashMap<String, Consumer<MessageResult>> topicCallbacks = new ConcurrentHashMap<>();
    private final AtomicBoolean consumerStarted = new AtomicBoolean(false);
    private volatile Channel resultChannel;

    public RabbitMqMessageTransport(RabbitMqConnectionManager connectionManager,
                                    RabbitMqTransportConfig config) {
        this.connectionManager = connectionManager;
        this.config = config;
        this.correlationIdResolver = new CorrelationIdResolver();
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    @Override
    public void send(String topic, UUID correlationId, Map<String, Object> payload) {
        try {
            byte[] body = objectMapper.writeValueAsBytes(payload);
            AMQP.BasicProperties properties = correlationIdResolver.createProperties(correlationId, topic);

            try (Channel channel = connectionManager.createChannel()) {
                channel.basicPublish(config.getTasksExchange(), RabbitMqTopologyInitializer.EXECUTE_QUEUE, properties, body);
                log.debug("Sent message to topic '{}' with correlationId={}", topic, correlationId);
            }
        } catch (IOException | TimeoutException e) {
            throw new RuntimeException("Failed to send message to topic: " + topic, e);
        }
    }

    @Override
    public void subscribe(String topic, Consumer<MessageResult> callback) {
        topicCallbacks.put(topic, callback);
        log.info("Registered callback for topic '{}'", topic);

        if (consumerStarted.compareAndSet(false, true)) {
            startResultConsumer();
        }
    }

    @SuppressWarnings("unchecked")
    private void startResultConsumer() {
        try {
            resultChannel = connectionManager.createChannel();
            resultChannel.basicQos(1);

            resultChannel.basicConsume(RabbitMqTopologyInitializer.RESULT_QUEUE, false, new DefaultConsumer(resultChannel) {
                @Override
                public void handleDelivery(String consumerTag, Envelope envelope,
                                           AMQP.BasicProperties properties, byte[] body) throws IOException {
                    try {
                        String topic = correlationIdResolver.extractTopic(properties);
                        UUID correlationId = correlationIdResolver.extract(properties);
                        Map<String, Object> payload = objectMapper.readValue(body, Map.class);

                        Consumer<MessageResult> topicCallback = topicCallbacks.get(topic);
                        if (topicCallback == null) {
                            throw new IllegalStateException("No callback registered for topic: " + topic);
                        }

                        boolean success = true;
                        String errorCode = null;
                        if (payload.containsKey("__error")) {
                            success = false;
                            errorCode = String.valueOf(payload.get("__errorCode"));
                        }

                        MessageResult result = new MessageResult(correlationId, payload, success, errorCode);
                        topicCallback.accept(result);
                        resultChannel.basicAck(envelope.getDeliveryTag(), false);
                        log.debug("Processed result for topic '{}', correlationId={}", topic, correlationId);
                    } catch (Exception e) {
                        log.error("Error processing result message", e);
                        resultChannel.basicNack(envelope.getDeliveryTag(), false, false);
                    }
                }
            });

            log.info("Started shared result consumer on queue: {}", RabbitMqTopologyInitializer.RESULT_QUEUE);
        } catch (IOException | TimeoutException e) {
            throw new RuntimeException("Failed to start result consumer", e);
        }
    }

    public void close() {
        if (resultChannel != null && resultChannel.isOpen()) {
            try {
                resultChannel.close();
            } catch (IOException | TimeoutException e) {
                log.warn("Error closing result consumer channel", e);
            }
        }
    }
}
