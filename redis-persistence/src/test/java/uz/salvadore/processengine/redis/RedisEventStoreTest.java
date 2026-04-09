package uz.salvadore.processengine.redis;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import uz.salvadore.processengine.core.domain.event.ProcessEvent;
import uz.salvadore.processengine.core.domain.event.ProcessStartedEvent;
import uz.salvadore.processengine.core.domain.event.TaskCompletedEvent;
import uz.salvadore.processengine.core.domain.event.TokenMovedEvent;
import uz.salvadore.processengine.core.util.UUIDv7;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RedisEventStoreTest extends AbstractRedisTest {

    private RedisEventStore eventStore;

    @BeforeEach
    void setUp() {
        eventStore = new RedisEventStore(redisTemplate, objectMapper);
    }

    @Nested
    @DisplayName("append()")
    class Append {

        @Test
        @DisplayName("should store a single event and retrieve it via getEvents()")
        void shouldAppendSingleEvent() {
            // Arrange
            UUID processInstanceId = UUIDv7.generate();
            ProcessStartedEvent event = new ProcessStartedEvent(
                    UUIDv7.generate(),
                    processInstanceId,
                    UUIDv7.generate(),
                    null,
                    null,
                    Map.of("key", "value"),
                    Instant.now(),
                    1L
            );

            // Act
            eventStore.append(event);

            // Assert
            List<ProcessEvent> events = eventStore.getEvents(processInstanceId);
            assertThat(events).hasSize(1);
            assertThat(events.get(0)).isInstanceOf(ProcessStartedEvent.class);
            assertThat(events.get(0).id()).isEqualTo(event.id());
            assertThat(events.get(0).processInstanceId()).isEqualTo(processInstanceId);
            assertThat(events.get(0).sequenceNumber()).isEqualTo(1L);
        }
    }

    @Nested
    @DisplayName("appendAll()")
    class AppendAll {

        @Test
        @DisplayName("should store multiple events and retrieve all of them")
        void shouldAppendMultipleEvents() {
            // Arrange
            UUID processInstanceId = UUIDv7.generate();
            ProcessStartedEvent startedEvent = new ProcessStartedEvent(
                    UUIDv7.generate(),
                    processInstanceId,
                    UUIDv7.generate(),
                    null,
                    null,
                    Map.of(),
                    Instant.now(),
                    1L
            );
            TokenMovedEvent movedEvent = new TokenMovedEvent(
                    UUIDv7.generate(),
                    processInstanceId,
                    UUIDv7.generate(),
                    "start",
                    "task1",
                    Instant.now(),
                    2L
            );
            TaskCompletedEvent completedEvent = new TaskCompletedEvent(
                    UUIDv7.generate(),
                    processInstanceId,
                    UUIDv7.generate(),
                    "task1",
                    Map.of("result", "done"),
                    Instant.now(),
                    3L
            );

            // Act
            eventStore.appendAll(List.of(startedEvent, movedEvent, completedEvent));

            // Assert
            List<ProcessEvent> events = eventStore.getEvents(processInstanceId);
            assertThat(events).hasSize(3);
            assertThat(events).extracting(ProcessEvent::id)
                    .containsExactly(startedEvent.id(), movedEvent.id(), completedEvent.id());
        }
    }

    @Nested
    @DisplayName("getEvents()")
    class GetEvents {

        @Test
        @DisplayName("should return empty list for unknown processInstanceId")
        void shouldReturnEmptyListForUnknownProcessInstanceId() {
            // Arrange
            UUID unknownId = UUIDv7.generate();

            // Act
            List<ProcessEvent> events = eventStore.getEvents(unknownId);

            // Assert
            assertThat(events).isEmpty();
        }

        @Test
        @DisplayName("should return events sorted by sequenceNumber")
        void shouldReturnEventsSortedBySequenceNumber() {
            // Arrange
            UUID processInstanceId = UUIDv7.generate();

            // Append events out of order (sequence 3, 1, 2)
            TokenMovedEvent event3 = new TokenMovedEvent(
                    UUIDv7.generate(),
                    processInstanceId,
                    UUIDv7.generate(),
                    "task1",
                    "end",
                    Instant.now(),
                    3L
            );
            ProcessStartedEvent event1 = new ProcessStartedEvent(
                    UUIDv7.generate(),
                    processInstanceId,
                    UUIDv7.generate(),
                    null,
                    null,
                    Map.of(),
                    Instant.now(),
                    1L
            );
            TokenMovedEvent event2 = new TokenMovedEvent(
                    UUIDv7.generate(),
                    processInstanceId,
                    UUIDv7.generate(),
                    "start",
                    "task1",
                    Instant.now(),
                    2L
            );

            eventStore.append(event3);
            eventStore.append(event1);
            eventStore.append(event2);

            // Act
            List<ProcessEvent> events = eventStore.getEvents(processInstanceId);

            // Assert
            assertThat(events).hasSize(3);
            assertThat(events).extracting(ProcessEvent::sequenceNumber)
                    .containsExactly(1L, 2L, 3L);
        }
    }
}
