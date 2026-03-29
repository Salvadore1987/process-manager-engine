package uz.salvadore.processengine.core.adapter.noop;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uz.salvadore.processengine.core.domain.event.ProcessEvent;
import uz.salvadore.processengine.core.domain.event.ProcessStartedEvent;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

@DisplayName("NoOpEventStore")
class NoOpEventStoreTest {

    private NoOpEventStore eventStore;

    @BeforeEach
    void setUp() {
        eventStore = new NoOpEventStore();
    }

    @Test
    @DisplayName("3.3.12 append should not throw any exception (no-op)")
    void appendShouldBeNoOp() {
        // Arrange
        ProcessStartedEvent event = new ProcessStartedEvent(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null,
                Map.of(), Instant.now(), 1L
        );

        // Act & Assert
        assertThatCode(() -> eventStore.append(event)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("getEvents should always return empty list")
    void getEventsShouldReturnEmptyList() {
        // Arrange
        UUID processInstanceId = UUID.randomUUID();
        eventStore.append(new ProcessStartedEvent(
                UUID.randomUUID(), processInstanceId, UUID.randomUUID(), null,
                Map.of(), Instant.now(), 1L
        ));

        // Act
        List<ProcessEvent> events = eventStore.getEvents(processInstanceId);

        // Assert
        assertThat(events).isEmpty();
    }

    @Test
    @DisplayName("appendAll should not throw any exception (no-op)")
    void appendAllShouldBeNoOp() {
        // Arrange
        ProcessStartedEvent event1 = new ProcessStartedEvent(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null,
                Map.of(), Instant.now(), 1L
        );
        ProcessStartedEvent event2 = new ProcessStartedEvent(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null,
                Map.of(), Instant.now(), 2L
        );
        List<ProcessEvent> events = List.of(event1, event2);

        // Act & Assert
        assertThatCode(() -> eventStore.appendAll(events)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("getEvents for any processInstanceId should return empty list")
    void getEventsForAnyIdShouldReturnEmptyList() {
        // Arrange
        UUID randomId = UUID.randomUUID();

        // Act
        List<ProcessEvent> events = eventStore.getEvents(randomId);

        // Assert
        assertThat(events).isEmpty();
    }
}
