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
        @DisplayName("creates all four exchanges, DLQ queue, and shared task queues")
        void createsAllExchangesDlqQueueAndSharedQueues() throws IOException, TimeoutException {
            // Act
            topologyInitializer.initializeTopology();

            // Assert — verify exchanges and queues exist by passively declaring them
            try (Channel channel = connectionManager.createChannel()) {
                channel.exchangeDeclarePassive(config.getTasksExchange());
                channel.exchangeDeclarePassive(config.getRetryExchange());
                channel.exchangeDeclarePassive(config.getDlqExchange());
                channel.exchangeDeclarePassive(config.getTimersExchange());

                channel.queueDeclarePassive("process-engine.dlq");
                channel.queueDeclarePassive(RabbitMqTopologyInitializer.EXECUTE_QUEUE);
                channel.queueDeclarePassive(RabbitMqTopologyInitializer.RESULT_QUEUE);
                channel.queueDeclarePassive(RabbitMqTopologyInitializer.RETRY_QUEUE);
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
}
