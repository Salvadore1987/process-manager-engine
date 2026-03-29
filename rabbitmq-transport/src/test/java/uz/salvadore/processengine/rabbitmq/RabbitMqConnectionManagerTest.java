package uz.salvadore.processengine.rabbitmq;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import uz.salvadore.processengine.rabbitmq.config.RabbitMqTransportConfig;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@Tag("integration")
@DisplayName("RabbitMqConnectionManager")
class RabbitMqConnectionManagerTest {

    @Container
    static final RabbitMQContainer RABBIT = new RabbitMQContainer("rabbitmq:3.13-management")
            .withExposedPorts(5672, 15672);

    private RabbitMqTransportConfig config;
    private RabbitMqConnectionManager connectionManager;

    @BeforeEach
    void setUp() {
        config = RabbitMqTransportConfig.builder()
                .host(RABBIT.getHost())
                .port(RABBIT.getAmqpPort())
                .username("guest")
                .password("guest")
                .build();
        connectionManager = new RabbitMqConnectionManager(config);
    }

    @AfterEach
    void tearDown() {
        connectionManager.close();
    }

    @Test
    @DisplayName("getConnection returns an open connection")
    void getConnectionReturnsOpenConnection() throws Exception {
        // Act
        Connection connection = connectionManager.getConnection();

        // Assert
        assertThat(connection).isNotNull();
        assertThat(connection.isOpen()).isTrue();
    }

    @Test
    @DisplayName("getConnection returns the same connection instance on subsequent calls")
    void getConnectionReturnsSameInstance() throws Exception {
        // Act
        Connection first = connectionManager.getConnection();
        Connection second = connectionManager.getConnection();

        // Assert
        assertThat(first).isSameAs(second);
    }

    @Test
    @DisplayName("createChannel returns a usable open channel")
    void createChannelReturnsUsableChannel() throws Exception {
        // Act
        Channel channel = connectionManager.createChannel();

        // Assert
        assertThat(channel).isNotNull();
        assertThat(channel.isOpen()).isTrue();
        channel.close();
    }

    @Test
    @DisplayName("isConnected returns true after connection is established")
    void isConnectedReturnsTrueAfterConnection() throws Exception {
        // Arrange
        connectionManager.getConnection();

        // Act
        boolean connected = connectionManager.isConnected();

        // Assert
        assertThat(connected).isTrue();
    }

    @Test
    @DisplayName("isConnected returns false before any connection is made")
    void isConnectedReturnsFalseBeforeConnection() {
        // Act
        boolean connected = connectionManager.isConnected();

        // Assert
        assertThat(connected).isFalse();
    }

    @Test
    @DisplayName("isConnected returns false after close")
    void isConnectedReturnsFalseAfterClose() throws Exception {
        // Arrange
        connectionManager.getConnection();

        // Act
        connectionManager.close();

        // Assert
        assertThat(connectionManager.isConnected()).isFalse();
    }

    @Test
    @DisplayName("close gracefully closes the connection")
    void closeGracefullyClosesConnection() throws Exception {
        // Arrange
        Connection connection = connectionManager.getConnection();
        assertThat(connection.isOpen()).isTrue();

        // Act
        connectionManager.close();

        // Assert
        assertThat(connection.isOpen()).isFalse();
    }

    @Test
    @DisplayName("getConnection throws IllegalStateException after close")
    void getConnectionThrowsAfterClose() {
        // Arrange
        connectionManager.close();

        // Act & Assert
        assertThatThrownBy(() -> connectionManager.getConnection())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("closed");
    }

    @Test
    @DisplayName("close is idempotent — calling twice does not throw")
    void closeIsIdempotent() throws Exception {
        // Arrange
        connectionManager.getConnection();

        // Act & Assert — no exception on second close
        connectionManager.close();
        connectionManager.close();
    }
}
