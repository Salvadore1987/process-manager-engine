package uz.salvadore.processengine.rabbitmq.consumer;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uz.salvadore.processengine.rabbitmq.RabbitMqConnectionManager;
import uz.salvadore.processengine.rabbitmq.correlation.CorrelationIdResolver;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;
import java.util.function.BiConsumer;

/**
 * Consumes task result messages from result queues and dispatches
 * callbacks using virtual threads for non-blocking processing.
 */
public final class TaskResultConsumer {

    private static final Logger log = LoggerFactory.getLogger(TaskResultConsumer.class);

    private final RabbitMqConnectionManager connectionManager;
    private final CorrelationIdResolver correlationIdResolver;
    private final ObjectMapper objectMapper;
    private final ExecutorService executor;
    private Channel channel;

    public TaskResultConsumer(RabbitMqConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
        this.correlationIdResolver = new CorrelationIdResolver();
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.executor = Executors.newVirtualThreadPerTaskExecutor();
    }

    @SuppressWarnings("unchecked")
    public void consume(String resultQueue, BiConsumer<UUID, Map<String, Object>> callback)
            throws IOException, TimeoutException {
        this.channel = connectionManager.createChannel();

        channel.basicConsume(resultQueue, false, new DefaultConsumer(channel) {
            @Override
            public void handleDelivery(String consumerTag, Envelope envelope,
                                       AMQP.BasicProperties properties, byte[] body) throws IOException {
                try {
                    UUID correlationId = correlationIdResolver.extract(properties);
                    Map<String, Object> payload = objectMapper.readValue(body, Map.class);

                    executor.submit(() -> {
                        try {
                            callback.accept(correlationId, payload);
                        } catch (Exception e) {
                            log.error("Error in result callback for correlationId={}", correlationId, e);
                        }
                    });

                    channel.basicAck(envelope.getDeliveryTag(), false);
                } catch (Exception e) {
                    log.error("Error processing result message", e);
                    channel.basicNack(envelope.getDeliveryTag(), false, false);
                }
            }
        });

        log.info("TaskResultConsumer started on queue: {}", resultQueue);
    }

    public void close() {
        if (channel != null && channel.isOpen()) {
            try {
                channel.close();
            } catch (IOException | TimeoutException e) {
                log.warn("Error closing TaskResultConsumer channel", e);
            }
        }
        executor.shutdown();
    }
}
