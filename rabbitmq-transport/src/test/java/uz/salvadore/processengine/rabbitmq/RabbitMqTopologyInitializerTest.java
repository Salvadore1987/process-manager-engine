package uz.salvadore.processengine.rabbitmq;

import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import uz.salvadore.processengine.rabbitmq.config.RabbitMqTransportConfig;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

@Testcontainers
@Tag("integration")
@DisplayName("RabbitMqTopologyInitializer")
class RabbitMqTopologyInitializerTest {

    @Container
    static final RabbitMQContainer RABBIT = new RabbitMQContainer("rabbitmq:3.13-management")
            .withExposedPorts(5672, 15672);

    private RabbitMqTransportConfig config;
    private RabbitMqConnectionManager connectionManager;
    private RabbitMqTopologyInitializer topologyInitializer;

    @BeforeEach
    void setUp() {
        config = RabbitMqTransportConfig.builder()
                .host(RABBIT.getHost())
                .port(RABBIT.getAmqpPort())
                .username("guest")
                .password("guest")
                .build();
        connectionManager = new RabbitMqConnectionManager(config);
        topologyInitializer = new RabbitMqTopologyInitializer(connectionManager, config);
    }

    @AfterEach
    void tearDown() {
        connectionManager.close();
    }

    @Nested
    @DisplayName("initializeTopology")
    class InitializeTopology {

        @Test
        @DisplayName("creates all four exchanges and the DLQ queue")
        void createsAllExchangesAndDlqQueue() throws IOException, TimeoutException {
            // Act
            topologyInitializer.initializeTopology();

            // Assert — verify exchanges and queue exist by passively declaring them
            try (Channel channel = connectionManager.createChannel()) {
                // exchangeDeclarePassive throws if exchange does not exist
                channel.exchangeDeclarePassive(config.getTasksExchange());
                channel.exchangeDeclarePassive(config.getRetryExchange());
                channel.exchangeDeclarePassive(config.getDlqExchange());
                channel.exchangeDeclarePassive(config.getTimersExchange());

                // queueDeclarePassive throws if queue does not exist
                channel.queueDeclarePassive("process-engine.dlq");
            }
        }

        @Test
        @DisplayName("is idempotent — calling twice does not throw")
        void isIdempotent() throws IOException, TimeoutException {
            // Act & Assert
            topologyInitializer.initializeTopology();
            assertThatCode(() -> topologyInitializer.initializeTopology())
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("ensureTopicQueues")
    class EnsureTopicQueues {

        @BeforeEach
        void initTopology() throws IOException, TimeoutException {
            topologyInitializer.initializeTopology();
        }

        @Test
        @DisplayName("creates execute, result, and retry queues for a given topic")
        void createsPerTopicQueues() throws IOException, TimeoutException {
            // Arrange
            String topic = "send-email";

            // Act
            topologyInitializer.ensureTopicQueues(topic);

            // Assert — verify all three queues exist
            try (Channel channel = connectionManager.createChannel()) {
                channel.queueDeclarePassive("task.send-email.execute");
                channel.queueDeclarePassive("task.send-email.result");
                channel.queueDeclarePassive("task.send-email.retry");
            }
        }

        @Test
        @DisplayName("tracks topic in declaredTopics set")
        void tracksTopicInDeclaredTopics() throws IOException, TimeoutException {
            // Arrange
            String topic = "notify-user";

            // Act
            topologyInitializer.ensureTopicQueues(topic);

            // Assert
            assertThat(topologyInitializer.getDeclaredTopics()).containsExactly(topic);
        }

        @Test
        @DisplayName("is idempotent — calling twice for the same topic does not throw")
        void isIdempotent() throws IOException, TimeoutException {
            // Arrange
            String topic = "validate-input";

            // Act & Assert
            topologyInitializer.ensureTopicQueues(topic);
            assertThatCode(() -> topologyInitializer.ensureTopicQueues(topic))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("handles multiple distinct topics independently")
        void handlesMultipleTopics() throws IOException, TimeoutException {
            // Arrange
            String topicA = "topic-alpha";
            String topicB = "topic-beta";

            // Act
            topologyInitializer.ensureTopicQueues(topicA);
            topologyInitializer.ensureTopicQueues(topicB);

            // Assert
            assertThat(topologyInitializer.getDeclaredTopics())
                    .containsExactlyInAnyOrder(topicA, topicB);

            try (Channel channel = connectionManager.createChannel()) {
                channel.queueDeclarePassive("task.topic-alpha.execute");
                channel.queueDeclarePassive("task.topic-beta.execute");
            }
        }

        @Test
        @DisplayName("getDeclaredTopics returns empty set before any topic is declared")
        void declaredTopicsEmptyInitially() {
            // Act & Assert
            assertThat(topologyInitializer.getDeclaredTopics()).isEmpty();
        }
    }
}
