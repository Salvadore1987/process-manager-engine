package uz.salvadore.processengine.rabbitmq.correlation;

import com.rabbitmq.client.AMQP;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Extracts and sets correlation-id and task-topic from/to AMQP message properties/headers.
 */
public final class CorrelationIdResolver {

    private static final String HEADER_CORRELATION_ID = "x-correlation-id";
    public static final String HEADER_TASK_TOPIC = "x-task-topic";

    public UUID extract(AMQP.BasicProperties properties) {
        if (properties.getCorrelationId() != null) {
            return UUID.fromString(properties.getCorrelationId());
        }
        Map<String, Object> headers = properties.getHeaders();
        if (headers != null && headers.containsKey(HEADER_CORRELATION_ID)) {
            return UUID.fromString(headers.get(HEADER_CORRELATION_ID).toString());
        }
        throw new IllegalArgumentException("No correlation-id found in message properties");
    }

    public String extractTopic(AMQP.BasicProperties properties) {
        Map<String, Object> headers = properties.getHeaders();
        if (headers != null && headers.containsKey(HEADER_TASK_TOPIC)) {
            return headers.get(HEADER_TASK_TOPIC).toString();
        }
        throw new IllegalArgumentException("No task topic found in message headers");
    }

    public AMQP.BasicProperties withCorrelationId(UUID correlationId, AMQP.BasicProperties properties) {
        Map<String, Object> headers = properties.getHeaders() != null
                ? new HashMap<>(properties.getHeaders())
                : new HashMap<>();
        headers.put(HEADER_CORRELATION_ID, correlationId.toString());

        return properties.builder()
                .correlationId(correlationId.toString())
                .headers(headers)
                .build();
    }

    public AMQP.BasicProperties createProperties(UUID correlationId) {
        Map<String, Object> headers = new HashMap<>();
        headers.put(HEADER_CORRELATION_ID, correlationId.toString());

        return new AMQP.BasicProperties.Builder()
                .correlationId(correlationId.toString())
                .contentType("application/json")
                .deliveryMode(2)
                .headers(headers)
                .build();
    }

    public AMQP.BasicProperties createProperties(UUID correlationId, String topic) {
        Map<String, Object> headers = new HashMap<>();
        headers.put(HEADER_CORRELATION_ID, correlationId.toString());
        headers.put(HEADER_TASK_TOPIC, topic);

        return new AMQP.BasicProperties.Builder()
                .correlationId(correlationId.toString())
                .contentType("application/json")
                .deliveryMode(2)
                .headers(headers)
                .build();
    }
}
