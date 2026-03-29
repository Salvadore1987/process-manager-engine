package uz.salvadore.processengine.rabbitmq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.GetResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import uz.salvadore.processengine.core.port.outgoing.MessageTransport;
import uz.salvadore.processengine.rabbitmq.config.RabbitMqTransportConfig;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@Tag("integration")
@DisplayName("RabbitMqMessageTransport")
class RabbitMqMessageTransportTest {

    @Container
    static final RabbitMQContainer RABBIT = new RabbitMQContainer("rabbitmq:3.13-management")
            .withExposedPorts(5672, 15672);

    private RabbitMqTransportConfig config;
    private RabbitMqConnectionManager connectionManager;
    private RabbitMqTopologyInitializer topologyInitializer;
    private RabbitMqMessageTransport messageTransport;
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
        messageTransport = new RabbitMqMessageTransport(connectionManager, config, topologyInitializer);
    }

    @AfterEach
    void tearDown() {
        messageTransport.close();
        connectionManager.close();
    }

    @Nested
    @DisplayName("send")
    class Send {

        @Test
        @DisplayName("sends message to execute queue with correct correlationId and payload")
        void sendsMessageToExecuteQueue() throws Exception {
            // Arrange
            String topic = "send-email";
            UUID correlationId = UUID.randomUUID();
            Map<String, Object> payload = Map.of("to", "user@example.com", "subject", "Hello");

            // Act
            messageTransport.send(topic, correlationId, payload);

            // Assert — consume directly from the execute queue
            try (Channel channel = connectionManager.createChannel()) {
                GetResponse response = channel.basicGet("task.send-email.execute", true);

                assertThat(response).isNotNull();
                assertThat(response.getProps().getCorrelationId()).isEqualTo(correlationId.toString());

                @SuppressWarnings("unchecked")
                Map<String, Object> receivedPayload = objectMapper.readValue(response.getBody(), Map.class);
                assertThat(receivedPayload).containsEntry("to", "user@example.com");
                assertThat(receivedPayload).containsEntry("subject", "Hello");
            }
        }

        @Test
        @DisplayName("sets content type to application/json")
        void setsContentType() throws Exception {
            // Arrange
            String topic = "process-payment";
            UUID correlationId = UUID.randomUUID();
            Map<String, Object> payload = Map.of("amount", 100);

            // Act
            messageTransport.send(topic, correlationId, payload);

            // Assert
            try (Channel channel = connectionManager.createChannel()) {
                GetResponse response = channel.basicGet("task.process-payment.execute", true);

                assertThat(response).isNotNull();
                assertThat(response.getProps().getContentType()).isEqualTo("application/json");
            }
        }

        @Test
        @DisplayName("sets x-correlation-id header alongside correlationId property")
        void setsCorrelationIdHeader() throws Exception {
            // Arrange
            String topic = "audit-log";
            UUID correlationId = UUID.randomUUID();
            Map<String, Object> payload = Map.of("action", "login");

            // Act
            messageTransport.send(topic, correlationId, payload);

            // Assert
            try (Channel channel = connectionManager.createChannel()) {
                GetResponse response = channel.basicGet("task.audit-log.execute", true);

                assertThat(response).isNotNull();
                Map<String, Object> headers = response.getProps().getHeaders();
                assertThat(headers).isNotNull();
                assertThat(headers.get("x-correlation-id").toString())
                        .isEqualTo(correlationId.toString());
            }
        }
    }

    @Nested
    @DisplayName("subscribe")
    class Subscribe {

        @Test
        @DisplayName("callback receives correct MessageResult when result is published")
        void callbackReceivesMessageResult() throws Exception {
            // Arrange
            String topic = "calculate-tax";
            UUID correlationId = UUID.randomUUID();
            Map<String, Object> resultPayload = Map.of("taxAmount", 15.5);

            CountDownLatch latch = new CountDownLatch(1);
            AtomicReference<MessageTransport.MessageResult> receivedResult = new AtomicReference<>();

            messageTransport.subscribe(topic, result -> {
                receivedResult.set(result);
                latch.countDown();
            });

            // Act — publish a result message directly to the result queue
            try (Channel channel = connectionManager.createChannel()) {
                AMQP.BasicProperties properties = new AMQP.BasicProperties.Builder()
                        .correlationId(correlationId.toString())
                        .contentType("application/json")
                        .headers(Map.of("x-correlation-id", correlationId.toString()))
                        .build();

                byte[] body = objectMapper.writeValueAsBytes(resultPayload);
                channel.basicPublish(
                        config.getTasksExchange(),
                        "task.calculate-tax.result",
                        properties,
                        body
                );
            }

            // Assert
            boolean received = latch.await(10, TimeUnit.SECONDS);
            assertThat(received).isTrue();

            MessageTransport.MessageResult result = receivedResult.get();
            assertThat(result).isNotNull();
            assertThat(result.correlationId()).isEqualTo(correlationId);
            assertThat(result.success()).isTrue();
            assertThat(result.errorCode()).isNull();
            assertThat(result.payload()).containsEntry("taxAmount", 15.5);
        }

        @Test
        @DisplayName("callback receives error result when payload contains __error key")
        void callbackReceivesErrorResult() throws Exception {
            // Arrange
            String topic = "validate-doc";
            UUID correlationId = UUID.randomUUID();
            Map<String, Object> errorPayload = Map.of(
                    "__error", true,
                    "__errorCode", "VALIDATION_FAILED",
                    "details", "Missing required field"
            );

            CountDownLatch latch = new CountDownLatch(1);
            AtomicReference<MessageTransport.MessageResult> receivedResult = new AtomicReference<>();

            messageTransport.subscribe(topic, result -> {
                receivedResult.set(result);
                latch.countDown();
            });

            // Act
            try (Channel channel = connectionManager.createChannel()) {
                AMQP.BasicProperties properties = new AMQP.BasicProperties.Builder()
                        .correlationId(correlationId.toString())
                        .contentType("application/json")
                        .headers(Map.of("x-correlation-id", correlationId.toString()))
                        .build();

                byte[] body = objectMapper.writeValueAsBytes(errorPayload);
                channel.basicPublish(
                        config.getTasksExchange(),
                        "task.validate-doc.result",
                        properties,
                        body
                );
            }

            // Assert
            boolean received = latch.await(10, TimeUnit.SECONDS);
            assertThat(received).isTrue();

            MessageTransport.MessageResult result = receivedResult.get();
            assertThat(result).isNotNull();
            assertThat(result.correlationId()).isEqualTo(correlationId);
            assertThat(result.success()).isFalse();
            assertThat(result.errorCode()).isEqualTo("VALIDATION_FAILED");
        }
    }
}
