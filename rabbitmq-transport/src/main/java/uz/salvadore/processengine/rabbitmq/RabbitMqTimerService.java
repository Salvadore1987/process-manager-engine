package uz.salvadore.processengine.rabbitmq;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uz.salvadore.processengine.core.port.outgoing.TimerService;
import uz.salvadore.processengine.rabbitmq.config.RabbitMqTransportConfig;

import java.io.IOException;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

/**
 * RabbitMQ-based timer service implementation.
 * Uses TTL-based delayed queues to schedule timer events.
 * Each timer gets a dedicated temporary queue with TTL that dead-letters
 * to the timer result queue upon expiration.
 */
public final class RabbitMqTimerService implements TimerService {

    private static final Logger log = LoggerFactory.getLogger(RabbitMqTimerService.class);
    private static final String TIMER_RESULT_QUEUE = "process-engine.timers.fired";

    private final RabbitMqConnectionManager connectionManager;
    private final RabbitMqTransportConfig config;
    private final ObjectMapper objectMapper;
    private final Set<String> cancelledTimers = ConcurrentHashMap.newKeySet();
    private Channel consumerChannel;

    public RabbitMqTimerService(RabbitMqConnectionManager connectionManager,
                                RabbitMqTransportConfig config) {
        this.connectionManager = connectionManager;
        this.config = config;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    /**
     * Initialize the timer result queue and start consuming. Call once at startup.
     */
    public void initialize(Consumer<TimerCallback> globalCallback) throws IOException, TimeoutException {
        Channel setupChannel = connectionManager.createChannel();
        try {
            setupChannel.queueDeclare(TIMER_RESULT_QUEUE, true, false, false, null);
            setupChannel.queueBind(TIMER_RESULT_QUEUE, config.getTimersExchange(), "timer.fired");
        } finally {
            setupChannel.close();
        }

        this.consumerChannel = connectionManager.createChannel();
        consumerChannel.basicConsume(TIMER_RESULT_QUEUE, false, new DefaultConsumer(consumerChannel) {
            @Override
            @SuppressWarnings("unchecked")
            public void handleDelivery(String consumerTag, Envelope envelope,
                                       AMQP.BasicProperties properties, byte[] body) throws IOException {
                try {
                    Map<String, Object> payload = objectMapper.readValue(body, Map.class);
                    UUID processInstanceId = UUID.fromString((String) payload.get("processInstanceId"));
                    UUID tokenId = UUID.fromString((String) payload.get("tokenId"));
                    String nodeId = (String) payload.get("nodeId");

                    String timerKey = processInstanceId + ":" + tokenId;
                    if (cancelledTimers.remove(timerKey)) {
                        log.debug("Timer cancelled, ignoring: processInstanceId={}, tokenId={}",
                                processInstanceId, tokenId);
                        consumerChannel.basicAck(envelope.getDeliveryTag(), false);
                        return;
                    }

                    TimerCallback callback = new TimerCallback(processInstanceId, tokenId, nodeId);
                    globalCallback.accept(callback);
                    consumerChannel.basicAck(envelope.getDeliveryTag(), false);
                    log.debug("Timer fired: processInstanceId={}, tokenId={}, nodeId={}",
                            processInstanceId, tokenId, nodeId);
                } catch (Exception e) {
                    log.error("Error processing timer message", e);
                    consumerChannel.basicNack(envelope.getDeliveryTag(), false, false);
                }
            }
        });

        log.info("RabbitMqTimerService initialized, consuming from: {}", TIMER_RESULT_QUEUE);
    }

    @Override
    public void schedule(UUID processInstanceId, UUID tokenId, String nodeId,
                         Duration duration, Consumer<TimerCallback> callback) {
        try {
            long delayMs = duration.toMillis();
            String timerQueue = "process-engine.timer." + processInstanceId + "." + tokenId;

            try (Channel channel = connectionManager.createChannel()) {
                channel.queueDeclare(timerQueue, false, false, true, Map.of(
                        "x-message-ttl", delayMs,
                        "x-dead-letter-exchange", config.getTimersExchange(),
                        "x-dead-letter-routing-key", "timer.fired",
                        "x-expires", delayMs + 60000
                ));

                Map<String, Object> payload = new HashMap<>();
                payload.put("processInstanceId", processInstanceId.toString());
                payload.put("tokenId", tokenId.toString());
                payload.put("nodeId", nodeId);

                byte[] body = objectMapper.writeValueAsBytes(payload);
                AMQP.BasicProperties properties = new AMQP.BasicProperties.Builder()
                        .contentType("application/json")
                        .deliveryMode(2)
                        .build();

                channel.basicPublish("", timerQueue, properties, body);
                log.debug("Timer scheduled: processInstanceId={}, tokenId={}, duration={}",
                        processInstanceId, tokenId, duration);
            }
        } catch (IOException | TimeoutException e) {
            throw new RuntimeException("Failed to schedule timer", e);
        }
    }

    @Override
    public void cancel(UUID processInstanceId, UUID tokenId) {
        String timerKey = processInstanceId + ":" + tokenId;
        cancelledTimers.add(timerKey);
        log.debug("Timer cancel requested: processInstanceId={}, tokenId={}",
                processInstanceId, tokenId);
    }

    public void close() {
        if (consumerChannel != null && consumerChannel.isOpen()) {
            try {
                consumerChannel.close();
            } catch (IOException | TimeoutException e) {
                log.warn("Error closing timer consumer channel", e);
            }
        }
    }
}
