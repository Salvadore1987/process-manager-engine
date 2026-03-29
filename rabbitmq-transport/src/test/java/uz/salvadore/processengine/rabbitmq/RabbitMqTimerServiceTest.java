package uz.salvadore.processengine.rabbitmq;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import uz.salvadore.processengine.core.port.outgoing.TimerService;
import uz.salvadore.processengine.rabbitmq.config.RabbitMqTransportConfig;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@Tag("integration")
@DisplayName("RabbitMqTimerService")
class RabbitMqTimerServiceTest {

    @Container
    static final RabbitMQContainer RABBIT = new RabbitMQContainer("rabbitmq:3.13-management")
            .withExposedPorts(5672, 15672);

    private RabbitMqTransportConfig config;
    private RabbitMqConnectionManager connectionManager;
    private RabbitMqTopologyInitializer topologyInitializer;
    private RabbitMqTimerService timerService;

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
        timerService = new RabbitMqTimerService(connectionManager, config);
    }

    @AfterEach
    void tearDown() {
        timerService.close();
        connectionManager.close();
    }

    @Test
    @DisplayName("scheduled timer fires callback within expected timeframe")
    void scheduledTimerFiresCallback() throws Exception {
        // Arrange
        UUID processInstanceId = UUID.randomUUID();
        UUID tokenId = UUID.randomUUID();
        String nodeId = "timer-node-1";
        Duration timerDuration = Duration.ofSeconds(1);

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<TimerService.TimerCallback> firedCallback = new AtomicReference<>();

        timerService.initialize(callback -> {
            firedCallback.set(callback);
            latch.countDown();
        });

        // Act
        timerService.schedule(processInstanceId, tokenId, nodeId, timerDuration, ignored -> {});

        // Assert — timer should fire within ~3 seconds (1s TTL + broker processing)
        boolean fired = latch.await(5, TimeUnit.SECONDS);
        assertThat(fired)
                .as("Timer should have fired within 5 seconds")
                .isTrue();

        TimerService.TimerCallback result = firedCallback.get();
        assertThat(result).isNotNull();
        assertThat(result.processInstanceId()).isEqualTo(processInstanceId);
        assertThat(result.tokenId()).isEqualTo(tokenId);
        assertThat(result.nodeId()).isEqualTo(nodeId);
    }

    @Test
    @DisplayName("timer callback contains correct processInstanceId, tokenId, and nodeId")
    void timerCallbackContainsCorrectPayload() throws Exception {
        // Arrange
        UUID processInstanceId = UUID.randomUUID();
        UUID tokenId = UUID.randomUUID();
        String nodeId = "approval-timeout";

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<TimerService.TimerCallback> firedCallback = new AtomicReference<>();

        timerService.initialize(callback -> {
            firedCallback.set(callback);
            latch.countDown();
        });

        // Act
        timerService.schedule(processInstanceId, tokenId, nodeId, Duration.ofSeconds(1), ignored -> {});

        // Assert
        boolean fired = latch.await(5, TimeUnit.SECONDS);
        assertThat(fired).isTrue();

        TimerService.TimerCallback result = firedCallback.get();
        assertThat(result.processInstanceId()).isEqualTo(processInstanceId);
        assertThat(result.tokenId()).isEqualTo(tokenId);
        assertThat(result.nodeId()).isEqualTo("approval-timeout");
    }

    @Test
    @DisplayName("cancelled timer does not invoke callback")
    void cancelledTimerDoesNotFireCallback() throws Exception {
        // Arrange
        UUID processInstanceId = UUID.randomUUID();
        UUID tokenId = UUID.randomUUID();
        String nodeId = "escalation-timer";

        CountDownLatch latch = new CountDownLatch(1);

        timerService.initialize(callback -> latch.countDown());

        // Act — schedule then immediately cancel
        timerService.schedule(processInstanceId, tokenId, nodeId, Duration.ofSeconds(1), ignored -> {});
        timerService.cancel(processInstanceId, tokenId);

        // Assert — callback should NOT fire within the expected window
        boolean fired = latch.await(5, TimeUnit.SECONDS);
        assertThat(fired)
                .as("Cancelled timer should not fire")
                .isFalse();
    }

    @Test
    @DisplayName("multiple timers fire independently with correct payloads")
    void multipleTimersFireIndependently() throws Exception {
        // Arrange
        UUID processId1 = UUID.randomUUID();
        UUID tokenId1 = UUID.randomUUID();
        UUID processId2 = UUID.randomUUID();
        UUID tokenId2 = UUID.randomUUID();

        CountDownLatch latch = new CountDownLatch(2);
        java.util.List<TimerService.TimerCallback> firedCallbacks =
                java.util.Collections.synchronizedList(new java.util.ArrayList<>());

        timerService.initialize(callback -> {
            firedCallbacks.add(callback);
            latch.countDown();
        });

        // Act
        timerService.schedule(processId1, tokenId1, "node-a", Duration.ofSeconds(1), ignored -> {});
        timerService.schedule(processId2, tokenId2, "node-b", Duration.ofSeconds(1), ignored -> {});

        // Assert
        boolean bothFired = latch.await(10, TimeUnit.SECONDS);
        assertThat(bothFired)
                .as("Both timers should have fired")
                .isTrue();

        assertThat(firedCallbacks).hasSize(2);
        assertThat(firedCallbacks)
                .extracting(TimerService.TimerCallback::processInstanceId)
                .containsExactlyInAnyOrder(processId1, processId2);
    }
}
