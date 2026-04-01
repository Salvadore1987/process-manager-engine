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
import java.util.function.Consumer;

/**
 * RabbitMQ implementation of MessageTransport.
 * Sends task execution messages and subscribes to result queues.
 */
public final class RabbitMqMessageTransport implements MessageTransport {

    private static final Logger log = LoggerFactory.getLogger(RabbitMqMessageTransport.class);

    private final RabbitMqConnectionManager connectionManager;
    private final RabbitMqTransportConfig config;
    private final RabbitMqTopologyInitializer topologyInitializer;
    private final CorrelationIdResolver correlationIdResolver;
    private final ObjectMapper objectMapper;
    private final ConcurrentHashMap<String, Channel> consumerChannels = new ConcurrentHashMap<>();

    public RabbitMqMessageTransport(RabbitMqConnectionManager connectionManager,
                                    RabbitMqTransportConfig config,
                                    RabbitMqTopologyInitializer topologyInitializer) {
        this.connectionManager = connectionManager;
        this.config = config;
        this.topologyInitializer = topologyInitializer;
        this.correlationIdResolver = new CorrelationIdResolver();
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    @Override
    public void send(String topic, UUID correlationId, Map<String, Object> payload) {
        try {
            topologyInitializer.ensureTopicQueues(topic);

            String routingKey = "task." + topic + ".execute";
            byte[] body = objectMapper.writeValueAsBytes(payload);
            AMQP.BasicProperties properties = correlationIdResolver.createProperties(correlationId);

            try (Channel channel = connectionManager.createChannel()) {
                channel.basicPublish(config.getTasksExchange(), routingKey, properties, body);
                log.debug("Sent message to topic '{}' with correlationId={}", topic, correlationId);
            }
        } catch (IOException | TimeoutException e) {
            throw new RuntimeException("Failed to send message to topic: " + topic, e);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public void subscribe(String topic, Consumer<MessageResult> callback) {
        if (consumerChannels.containsKey(topic)) {
            log.debug("Already subscribed to topic '{}', skipping", topic);
            return;
        }

        try {
            topologyInitializer.ensureTopicQueues(topic);

            String resultQueue = "task." + topic + ".result";
            Channel channel = connectionManager.createChannel();
            channel.basicQos(1);
            consumerChannels.put(topic, channel);

            channel.basicConsume(resultQueue, false, new DefaultConsumer(channel) {
                @Override
                public void handleDelivery(String consumerTag, Envelope envelope,
                                           AMQP.BasicProperties properties, byte[] body) throws IOException {
                    try {
                        UUID correlationId = correlationIdResolver.extract(properties);
                        Map<String, Object> payload = objectMapper.readValue(body, Map.class);

                        boolean success = true;
                        String errorCode = null;
                        if (payload.containsKey("__error")) {
                            success = false;
                            errorCode = String.valueOf(payload.get("__errorCode"));
                        }

                        MessageResult result = new MessageResult(correlationId, payload, success, errorCode);
                        callback.accept(result);
                        channel.basicAck(envelope.getDeliveryTag(), false);
                        log.debug("Processed result for topic '{}', correlationId={}", topic, correlationId);
                    } catch (Exception e) {
                        log.error("Error processing result message for topic '{}'", topic, e);
                        channel.basicNack(envelope.getDeliveryTag(), false, false);
                    }
                }
            });

            log.info("Subscribed to result queue: {}", resultQueue);
        } catch (IOException | TimeoutException e) {
            throw new RuntimeException("Failed to subscribe to topic: " + topic, e);
        }
    }

    public void close() {
        for (Map.Entry<String, Channel> entry : consumerChannels.entrySet()) {
            Channel channel = entry.getValue();
            if (channel.isOpen()) {
                try {
                    channel.close();
                } catch (IOException | TimeoutException e) {
                    log.warn("Error closing consumer channel for topic '{}'", entry.getKey(), e);
                }
            }
        }
        consumerChannels.clear();
    }
}
