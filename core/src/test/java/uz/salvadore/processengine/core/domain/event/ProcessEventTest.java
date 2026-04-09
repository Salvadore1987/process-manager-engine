package uz.salvadore.processengine.core.domain.event;

import org.junit.jupiter.api.Test;
import uz.salvadore.processengine.core.util.UUIDv7;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ProcessEventTest {

    private final UUID processInstanceId = UUIDv7.generate();

    @Test
    void shouldCreateProcessStartedEvent() {
        // Arrange
        UUID defId = UUIDv7.generate();

        // Act
        ProcessStartedEvent event = new ProcessStartedEvent(
                UUIDv7.generate(), processInstanceId, defId, null, null, Map.of("key", "val"), Instant.now(), 1);

        // Assert
        assertThat(event.processInstanceId()).isEqualTo(processInstanceId);
        assertThat(event.definitionId()).isEqualTo(defId);
        assertThat(event.parentProcessInstanceId()).isNull();
        assertThat(event.variables()).containsEntry("key", "val");
        assertThat(event.sequenceNumber()).isEqualTo(1);
    }

    @Test
    void shouldCreateTokenMovedEvent() {
        // Arrange & Act
        TokenMovedEvent event = new TokenMovedEvent(
                UUIDv7.generate(), processInstanceId, UUIDv7.generate(), "node1", "node2", Instant.now(), 2);

        // Assert
        assertThat(event.fromNodeId()).isEqualTo("node1");
        assertThat(event.toNodeId()).isEqualTo("node2");
    }

    @Test
    void shouldCreateTaskCompletedEvent() {
        // Arrange & Act
        TaskCompletedEvent event = new TaskCompletedEvent(
                UUIDv7.generate(), processInstanceId, UUIDv7.generate(), "task1",
                Map.of("result", "ok"), Instant.now(), 3);

        // Assert
        assertThat(event.nodeId()).isEqualTo("task1");
        assertThat(event.result()).containsEntry("result", "ok");
    }

    @Test
    void shouldCreateCallActivityEvents() {
        // Arrange
        UUID childId = UUIDv7.generate();
        UUID tokenId = UUIDv7.generate();

        // Act
        CallActivityStartedEvent started = new CallActivityStartedEvent(
                UUIDv7.generate(), processInstanceId, tokenId, "ca1", childId, "payment-processing", Instant.now(), 4);
        CallActivityCompletedEvent completed = new CallActivityCompletedEvent(
                UUIDv7.generate(), processInstanceId, tokenId, "ca1", childId, Instant.now(), 5);

        // Assert
        assertThat(started.childProcessInstanceId()).isEqualTo(childId);
        assertThat(started.calledElement()).isEqualTo("payment-processing");
        assertThat(completed.childProcessInstanceId()).isEqualTo(childId);
    }

    @Test
    void shouldPreserveSequenceNumberOrdering() {
        // Arrange & Act
        ProcessStartedEvent first = new ProcessStartedEvent(
                UUIDv7.generate(), processInstanceId, UUIDv7.generate(), null, null, Map.of(), Instant.now(), 1);
        TokenMovedEvent second = new TokenMovedEvent(
                UUIDv7.generate(), processInstanceId, UUIDv7.generate(), "a", "b", Instant.now(), 2);
        ProcessCompletedEvent third = new ProcessCompletedEvent(
                UUIDv7.generate(), processInstanceId, Instant.now(), 3);

        // Assert
        assertThat(first.sequenceNumber()).isLessThan(second.sequenceNumber());
        assertThat(second.sequenceNumber()).isLessThan(third.sequenceNumber());
    }

    @Test
    void shouldCreateAllEventTypes() {
        // Arrange & Act & Assert — verify all sealed permits compile and instantiate
        Instant now = Instant.now();
        UUID id = UUIDv7.generate();
        UUID tid = UUIDv7.generate();

        assertThat(new ProcessSuspendedEvent(id, processInstanceId, now, 1)).isInstanceOf(ProcessEvent.class);
        assertThat(new ProcessResumedEvent(id, processInstanceId, now, 2)).isInstanceOf(ProcessEvent.class);
        assertThat(new ProcessErrorEvent(id, processInstanceId, "ERR", "msg", now, 3)).isInstanceOf(ProcessEvent.class);
        assertThat(new TimerScheduledEvent(id, processInstanceId, tid, "n1", Duration.ofMinutes(30), now, 4)).isInstanceOf(ProcessEvent.class);
        assertThat(new TimerFiredEvent(id, processInstanceId, tid, "n1", now, 5)).isInstanceOf(ProcessEvent.class);
        assertThat(new CompensationTriggeredEvent(id, processInstanceId, "src", "comp", now, 6)).isInstanceOf(ProcessEvent.class);
    }
}
