package uz.salvadore.processengine.rabbitmq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.GetResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import uz.salvadore.processengine.rabbitmq.config.RabbitMqTransportConfig;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@Tag("integration")
@DisplayName("RabbitMqRetryFlow")
class RabbitMqRetryFlowTest {

    @Container
    static final RabbitMQContainer RABBIT = new RabbitMQContainer("rabbitmq:3.13-management")
            .withExposedPorts(5672, 15672);

    private RabbitMqTransportConfig config;
    private RabbitMqConnectionManager connectionManager;
    private RabbitMqTopologyInitializer topologyInitializer;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() throws Exception {
        config = RabbitMqTransportConfig.builder()
                .host(RABBIT.getHost())
                .port(RABBIT.getAmqpPort())
                .username("guest")
                .password("guest")
                .build();
        connectionManager = new RabbitMqConnectionManager(config);
        topologyInitializer = new RabbitMqTopologyInitializer(connectionManager, config);
        topologyInitializer.initializeTopology();
    }

    @AfterEach
    void tearDown() {
        connectionManager.close();
    }

    @Test
    @DisplayName("nacked message from execute queue is routed to DLQ")
    void nackedMessageRoutesToDlq() throws Exception {
        // Arrange
        String topic = "process-order";
        topologyInitializer.ensureTopicQueues(topic);

        UUID correlationId = UUID.randomUUID();
        Map<String, Object> payload = Map.of("orderId", "ORD-123");
        byte[] body = objectMapper.writeValueAsBytes(payload);

        AMQP.BasicProperties properties = new AMQP.BasicProperties.Builder()
                .correlationId(correlationId.toString())
                .contentType("application/json")
                .deliveryMode(2)
                .build();

        // Publish a message to the execute queue
        try (Channel publishChannel = connectionManager.createChannel()) {
            publishChannel.basicPublish(
                    config.getTasksExchange(),
                    "task.process-order.execute",
                    properties,
                    body
            );
        }

        // Act — consume and nack (reject without requeue) to trigger dead-lettering
        try (Channel consumeChannel = connectionManager.createChannel()) {
            GetResponse response = null;
            // Poll for the message with a short retry loop (message may take a moment to arrive)
            for (int i = 0; i < 10 && response == null; i++) {
                response = consumeChannel.basicGet("task.process-order.execute", false);
                if (response == null) {
                    TimeUnit.MILLISECONDS.sleep(200);
                }
            }

            assertThat(response)
                    .as("Message should be available in execute queue")
                    .isNotNull();

            // Reject without requeue — this triggers x-dead-letter-exchange routing to DLQ
            consumeChannel.basicNack(response.getEnvelope().getDeliveryTag(), false, false);
        }

        // Assert — message should arrive in the DLQ
        try (Channel dlqChannel = connectionManager.createChannel()) {
            GetResponse dlqResponse = null;
            for (int i = 0; i < 15 && dlqResponse == null; i++) {
                dlqResponse = dlqChannel.basicGet("process-engine.dlq", true);
                if (dlqResponse == null) {
                    TimeUnit.MILLISECONDS.sleep(200);
                }
            }

            assertThat(dlqResponse)
                    .as("Nacked message should arrive in DLQ")
                    .isNotNull();

            @SuppressWarnings("unchecked")
            Map<String, Object> dlqPayload = objectMapper.readValue(dlqResponse.getBody(), Map.class);
            assertThat(dlqPayload).containsEntry("orderId", "ORD-123");
        }
    }

    @Test
    @DisplayName("retry queue has dead-letter-exchange pointing back to tasks exchange")
    void retryQueueHasCorrectDeadLetterExchangeConfig() throws Exception {
        // Arrange
        String topic = "retry-test";
        topologyInitializer.ensureTopicQueues(topic);

        // Act — publish a message to the retry queue with TTL=0 so it expires immediately
        // and dead-letters back to the tasks exchange -> execute queue
        try (Channel channel = connectionManager.createChannel()) {
            Map<String, Object> payload = Map.of("retryAttempt", 1);
            byte[] body = objectMapper.writeValueAsBytes(payload);

            AMQP.BasicProperties properties = new AMQP.BasicProperties.Builder()
                    .contentType("application/json")
                    .deliveryMode(2)
                    .expiration("0")  // TTL=0ms, expire immediately
                    .build();

            channel.basicPublish(
                    config.getRetryExchange(),
                    "task.retry-test.retry",
                    properties,
                    body
            );
        }

        // Assert — the expired message from retry queue should dead-letter
        // back to execute queue (via x-dead-letter-exchange = tasks exchange,
        // x-dead-letter-routing-key = task.retry-test.execute)
        try (Channel channel = connectionManager.createChannel()) {
            GetResponse response = null;
            for (int i = 0; i < 15 && response == null; i++) {
                response = channel.basicGet("task.retry-test.execute", true);
                if (response == null) {
                    TimeUnit.MILLISECONDS.sleep(200);
                }
            }

            assertThat(response)
                    .as("Expired retry message should dead-letter to execute queue")
                    .isNotNull();

            @SuppressWarnings("unchecked")
            Map<String, Object> receivedPayload = objectMapper.readValue(response.getBody(), Map.class);
            assertThat(receivedPayload).containsEntry("retryAttempt", 1);
        }
    }
}
