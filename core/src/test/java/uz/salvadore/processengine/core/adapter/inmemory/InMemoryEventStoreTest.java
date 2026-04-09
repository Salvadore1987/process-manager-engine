package uz.salvadore.processengine.core.adapter.inmemory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import uz.salvadore.processengine.core.domain.event.ProcessEvent;
import uz.salvadore.processengine.core.domain.event.ProcessStartedEvent;
import uz.salvadore.processengine.core.domain.event.TokenMovedEvent;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("InMemoryEventStore")
class InMemoryEventStoreTest {

    private InMemoryEventStore eventStore;

    @BeforeEach
    void setUp() {
        eventStore = new InMemoryEventStore();
    }

    @Nested
    @DisplayName("3.3.10 append and getEvents")
    class AppendAndGetEventsTests {

        @Test
        @DisplayName("should append event and retrieve it by processInstanceId")
        void shouldAppendAndRetrieveEvent() {
            // Arrange
            UUID processInstanceId = UUID.randomUUID();
            ProcessStartedEvent event = new ProcessStartedEvent(
                    UUID.randomUUID(), processInstanceId, UUID.randomUUID(), null, null,
                    Map.of(), Instant.now(), 1L
            );

            // Act
            eventStore.append(event);
            List<ProcessEvent> events = eventStore.getEvents(processInstanceId);

            // Assert
            assertThat(events).hasSize(1);
            assertThat(events.get(0)).isEqualTo(event);
        }

        @Test
        @DisplayName("should return events sorted by sequenceNumber")
        void shouldReturnEventsSortedBySequenceNumber() {
            // Arrange
            UUID processInstanceId = UUID.randomUUID();
            UUID tokenId = UUID.randomUUID();
            Instant now = Instant.now();

            // Intentionally append out of order
            TokenMovedEvent event3 = new TokenMovedEvent(
                    UUID.randomUUID(), processInstanceId, tokenId, "node2", "node3", now, 3L
            );
            ProcessStartedEvent event1 = new ProcessStartedEvent(
                    UUID.randomUUID(), processInstanceId, UUID.randomUUID(), null, null,
                    Map.of(), now, 1L
            );
            TokenMovedEvent event2 = new TokenMovedEvent(
                    UUID.randomUUID(), processInstanceId, tokenId, "node1", "node2", now, 2L
            );

            eventStore.append(event3);
            eventStore.append(event1);
            eventStore.append(event2);

            // Act
            List<ProcessEvent> events = eventStore.getEvents(processInstanceId);

            // Assert
            assertThat(events).hasSize(3);
            assertThat(events.get(0).sequenceNumber()).isEqualTo(1L);
            assertThat(events.get(1).sequenceNumber()).isEqualTo(2L);
            assertThat(events.get(2).sequenceNumber()).isEqualTo(3L);
        }

        @Test
        @DisplayName("should return empty list for unknown processInstanceId")
        void shouldReturnEmptyListForUnknownId() {
            // Arrange
            UUID unknownId = UUID.randomUUID();

            // Act
            List<ProcessEvent> events = eventStore.getEvents(unknownId);

            // Assert
            assertThat(events).isEmpty();
        }

        @Test
        @DisplayName("should isolate events by processInstanceId")
        void shouldIsolateEventsByProcessInstanceId() {
            // Arrange
            UUID processId1 = UUID.randomUUID();
            UUID processId2 = UUID.randomUUID();
            ProcessStartedEvent event1 = new ProcessStartedEvent(
                    UUID.randomUUID(), processId1, UUID.randomUUID(), null, null,
                    Map.of(), Instant.now(), 1L
            );
            ProcessStartedEvent event2 = new ProcessStartedEvent(
                    UUID.randomUUID(), processId2, UUID.randomUUID(), null, null,
                    Map.of(), Instant.now(), 1L
            );

            // Act
            eventStore.append(event1);
            eventStore.append(event2);

            // Assert
            assertThat(eventStore.getEvents(processId1)).hasSize(1);
            assertThat(eventStore.getEvents(processId2)).hasSize(1);
            assertThat(eventStore.size()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("appendAll")
    class AppendAllTests {

        @Test
        @DisplayName("should append all events from a list")
        void shouldAppendAllEvents() {
            // Arrange
            UUID processInstanceId = UUID.randomUUID();
            UUID tokenId = UUID.randomUUID();
            Instant now = Instant.now();
            ProcessStartedEvent event1 = new ProcessStartedEvent(
                    UUID.randomUUID(), processInstanceId, UUID.randomUUID(), null, null,
                    Map.of(), now, 1L
            );
            TokenMovedEvent event2 = new TokenMovedEvent(
                    UUID.randomUUID(), processInstanceId, tokenId, "__start__", "task1", now, 2L
            );
            List<ProcessEvent> events = List.of(event1, event2);

            // Act
            eventStore.appendAll(events);

            // Assert
            List<ProcessEvent> retrieved = eventStore.getEvents(processInstanceId);
            assertThat(retrieved).hasSize(2);
            assertThat(retrieved.get(0).sequenceNumber()).isEqualTo(1L);
            assertThat(retrieved.get(1).sequenceNumber()).isEqualTo(2L);
        }

        @Test
        @DisplayName("should handle empty list without error")
        void shouldHandleEmptyList() {
            // Arrange
            List<ProcessEvent> emptyList = List.of();

            // Act
            eventStore.appendAll(emptyList);

            // Assert
            assertThat(eventStore.size()).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("3.3.11 concurrent appends (thread safety)")
    class ConcurrentAppendTests {

        @Test
        @DisplayName("should handle concurrent appends from 10 threads each appending 100 events")
        void shouldHandleConcurrentAppends() throws InterruptedException {
            // Arrange
            UUID processInstanceId = UUID.randomUUID();
            int threadCount = 10;
            int eventsPerThread = 100;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(threadCount);

            // Act
            for (int t = 0; t < threadCount; t++) {
                int threadIndex = t;
                executor.submit(() -> {
                    try {
                        for (int i = 0; i < eventsPerThread; i++) {
                            long seqNum = (long) threadIndex * eventsPerThread + i + 1;
                            ProcessStartedEvent event = new ProcessStartedEvent(
                                    UUID.randomUUID(), processInstanceId, UUID.randomUUID(), null, null,
                                    Map.of(), Instant.now(), seqNum
                            );
                            eventStore.append(event);
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }
            boolean completed = latch.await(10, TimeUnit.SECONDS);

            // Assert
            assertThat(completed).isTrue();
            List<ProcessEvent> events = eventStore.getEvents(processInstanceId);
            assertThat(events).hasSize(threadCount * eventsPerThread);
            assertThat(eventStore.size()).isEqualTo(threadCount * eventsPerThread);

            executor.shutdown();
        }
    }

    @Nested
    @DisplayName("size and clear")
    class SizeAndClearTests {

        @Test
        @DisplayName("size should reflect total event count across all process instances")
        void sizeShouldReflectTotalCount() {
            // Arrange
            UUID processId1 = UUID.randomUUID();
            UUID processId2 = UUID.randomUUID();
            eventStore.append(new ProcessStartedEvent(
                    UUID.randomUUID(), processId1, UUID.randomUUID(), null, null, Map.of(), Instant.now(), 1L));
            eventStore.append(new ProcessStartedEvent(
                    UUID.randomUUID(), processId2, UUID.randomUUID(), null, null, Map.of(), Instant.now(), 1L));

            // Act & Assert
            assertThat(eventStore.size()).isEqualTo(2);
        }

        @Test
        @DisplayName("clear should remove all events")
        void clearShouldRemoveAllEvents() {
            // Arrange
            UUID processInstanceId = UUID.randomUUID();
            eventStore.append(new ProcessStartedEvent(
                    UUID.randomUUID(), processInstanceId, UUID.randomUUID(), null, null, Map.of(), Instant.now(), 1L));

            // Act
            eventStore.clear();

            // Assert
            assertThat(eventStore.size()).isEqualTo(0);
            assertThat(eventStore.getEvents(processInstanceId)).isEmpty();
        }
    }
}
