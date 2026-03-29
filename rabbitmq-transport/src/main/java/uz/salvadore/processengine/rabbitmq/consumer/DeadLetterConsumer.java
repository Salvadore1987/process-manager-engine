package uz.salvadore.processengine.rabbitmq.consumer;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uz.salvadore.processengine.rabbitmq.RabbitMqConnectionManager;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeoutException;

/**
 * Consumes messages from the dead-letter queue (DLQ).
 * Records incident information for failed messages that exhausted retries.
 */
public final class DeadLetterConsumer {

    private static final Logger log = LoggerFactory.getLogger(DeadLetterConsumer.class);

    private final RabbitMqConnectionManager connectionManager;
    private final List<DeadLetterRecord> deadLetters = new CopyOnWriteArrayList<>();
    private Channel channel;

    public DeadLetterConsumer(RabbitMqConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    public void consume(String dlqQueue) throws IOException, TimeoutException {
        this.channel = connectionManager.createChannel();

        channel.basicConsume(dlqQueue, false, new DefaultConsumer(channel) {
            @Override
            public void handleDelivery(String consumerTag, Envelope envelope,
                                       AMQP.BasicProperties properties, byte[] body) throws IOException {
                String messageBody = new String(body, StandardCharsets.UTF_8);
                String correlationId = properties.getCorrelationId();
                String routingKey = envelope.getRoutingKey();

                Map<String, Object> headers = properties.getHeaders();
                String reason = "exhausted_retries";
                if (headers != null && headers.containsKey("x-death-reason")) {
                    reason = headers.get("x-death-reason").toString();
                }

                DeadLetterRecord record = new DeadLetterRecord(
                        correlationId, routingKey, messageBody, reason
                );
                deadLetters.add(record);

                log.warn("Dead letter received: correlationId={}, routingKey={}, reason={}",
                        correlationId, routingKey, reason);

                channel.basicAck(envelope.getDeliveryTag(), false);
            }
        });

        log.info("DeadLetterConsumer started on queue: {}", dlqQueue);
    }

    public List<DeadLetterRecord> getDeadLetters() {
        return List.copyOf(deadLetters);
    }

    public void close() {
        if (channel != null && channel.isOpen()) {
            try {
                channel.close();
            } catch (IOException | TimeoutException e) {
                log.warn("Error closing DeadLetterConsumer channel", e);
            }
        }
    }

    public record DeadLetterRecord(String correlationId, String routingKey, String body, String reason) {}
}
