package uz.salvadore.processengine.core.engine.handler;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import uz.salvadore.processengine.core.adapter.inmemory.InMemorySequenceGenerator;
import uz.salvadore.processengine.core.domain.enums.ProcessState;
import uz.salvadore.processengine.core.domain.event.ProcessEvent;
import uz.salvadore.processengine.core.domain.event.TimerScheduledEvent;
import uz.salvadore.processengine.core.domain.model.EndEvent;
import uz.salvadore.processengine.core.domain.model.ProcessDefinition;
import uz.salvadore.processengine.core.domain.model.ProcessInstance;
import uz.salvadore.processengine.core.domain.model.SequenceFlow;
import uz.salvadore.processengine.core.domain.model.StartEvent;
import uz.salvadore.processengine.core.domain.model.TimerDefinition;
import uz.salvadore.processengine.core.domain.model.TimerIntermediateCatchEvent;
import uz.salvadore.processengine.core.domain.model.Token;
import uz.salvadore.processengine.core.engine.context.ExecutionContext;
import uz.salvadore.processengine.core.port.outgoing.TimerService;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

class TimerIntermediateCatchEventHandlerTest {

    /**
     * Records schedule calls for verification without external mocking libraries.
     */
    static class RecordingTimerService implements TimerService {
        final List<ScheduleCall> scheduleCalls = new ArrayList<>();

        record ScheduleCall(UUID processInstanceId, UUID tokenId, String nodeId, Duration duration) {}

        @Override
        public void schedule(UUID processInstanceId, UUID tokenId, String nodeId,
                             Duration duration, Consumer<TimerCallback> callback) {
            scheduleCalls.add(new ScheduleCall(processInstanceId, tokenId, nodeId, duration));
        }

        @Override
        public void cancel(UUID processInstanceId, UUID tokenId) {
            // no-op for tests
        }
    }

    private final RecordingTimerService timerService = new RecordingTimerService();
    private final InMemorySequenceGenerator sequenceGenerator = new InMemorySequenceGenerator();
    private final TimerIntermediateCatchEventHandler handler =
            new TimerIntermediateCatchEventHandler(timerService, sequenceGenerator);

    private ExecutionContext createContext(UUID processInstanceId, ProcessDefinition definition, Token token) {
        ProcessInstance instance = ProcessInstance.restore(
                processInstanceId, definition.getId(), null, ProcessState.RUNNING,
                List.of(token), Map.of(), Instant.now(), null
        );
        return new ExecutionContext(instance, definition);
    }

    private ProcessDefinition createMinimalDefinition(TimerIntermediateCatchEvent timerEvent) {
        StartEvent startEvent = new StartEvent("start1", "Start", List.of(), List.of("flow_to_timer"));
        EndEvent endEvent = new EndEvent("end1", "End", List.of("flow_from_timer"), List.of(), null);
        SequenceFlow flowToTimer = new SequenceFlow("flow_to_timer", "start1", timerEvent.id(), null);
        SequenceFlow flowFromTimer = new SequenceFlow("flow_from_timer", timerEvent.id(), "end1", null);

        return ProcessDefinition.create("test-process", 1, "Test Process", "<xml/>",
                List.of(startEvent, timerEvent, endEvent),
                List.of(flowToTimer, flowFromTimer));
    }

    @Nested
    @DisplayName("DURATION type timer")
    class DurationTypeTests {

        @Test
        @DisplayName("Should schedule timer with correct duration and return TimerScheduledEvent")
        void shouldScheduleTimerWithCorrectDuration() {
            // Arrange
            TimerDefinition timerDef = new TimerDefinition(TimerDefinition.TimerType.DURATION, "PT30M");
            TimerIntermediateCatchEvent timerEvent = new TimerIntermediateCatchEvent(
                    "timer1", "Wait 30 minutes",
                    List.of("flow_to_timer"), List.of("flow_from_timer"), timerDef);
            ProcessDefinition definition = createMinimalDefinition(timerEvent);
            Token token = Token.create("timer1");
            UUID processInstanceId = UUID.randomUUID();
            ExecutionContext context = createContext(processInstanceId, definition, token);

            // Act
            List<ProcessEvent> events = handler.handle(token, timerEvent, context);

            // Assert
            assertThat(events).hasSize(1);
            assertThat(events.getFirst()).isInstanceOf(TimerScheduledEvent.class);

            TimerScheduledEvent scheduledEvent = (TimerScheduledEvent) events.getFirst();
            assertThat(scheduledEvent.processInstanceId()).isEqualTo(processInstanceId);
            assertThat(scheduledEvent.tokenId()).isEqualTo(token.getId());
            assertThat(scheduledEvent.nodeId()).isEqualTo("timer1");
            assertThat(scheduledEvent.duration()).isEqualTo(Duration.ofMinutes(30));
            assertThat(scheduledEvent.sequenceNumber()).isEqualTo(1L);

            assertThat(timerService.scheduleCalls).hasSize(1);
            RecordingTimerService.ScheduleCall call = timerService.scheduleCalls.getFirst();
            assertThat(call.processInstanceId()).isEqualTo(processInstanceId);
            assertThat(call.tokenId()).isEqualTo(token.getId());
            assertThat(call.nodeId()).isEqualTo("timer1");
            assertThat(call.duration()).isEqualTo(Duration.ofMinutes(30));
        }

        @Test
        @DisplayName("Should handle PT5S short duration")
        void shouldHandleShortDuration() {
            // Arrange
            TimerDefinition timerDef = new TimerDefinition(TimerDefinition.TimerType.DURATION, "PT5S");
            TimerIntermediateCatchEvent timerEvent = new TimerIntermediateCatchEvent(
                    "timer1", "Wait 5 seconds",
                    List.of("flow_to_timer"), List.of("flow_from_timer"), timerDef);
            ProcessDefinition definition = createMinimalDefinition(timerEvent);
            Token token = Token.create("timer1");
            ExecutionContext context = createContext(UUID.randomUUID(), definition, token);

            // Act
            List<ProcessEvent> events = handler.handle(token, timerEvent, context);

            // Assert
            TimerScheduledEvent scheduledEvent = (TimerScheduledEvent) events.getFirst();
            assertThat(scheduledEvent.duration()).isEqualTo(Duration.ofSeconds(5));
        }
    }

    @Nested
    @DisplayName("DATE type timer")
    class DateTypeTests {

        @Test
        @DisplayName("Should schedule timer with positive duration for future date")
        void shouldScheduleTimerForFutureDate() {
            // Arrange
            Instant futureDate = Instant.now().plus(Duration.ofHours(2));
            TimerDefinition timerDef = new TimerDefinition(
                    TimerDefinition.TimerType.DATE, futureDate.toString());
            TimerIntermediateCatchEvent timerEvent = new TimerIntermediateCatchEvent(
                    "timer1", "Wait until future date",
                    List.of("flow_to_timer"), List.of("flow_from_timer"), timerDef);
            ProcessDefinition definition = createMinimalDefinition(timerEvent);
            Token token = Token.create("timer1");
            ExecutionContext context = createContext(UUID.randomUUID(), definition, token);

            // Act
            List<ProcessEvent> events = handler.handle(token, timerEvent, context);

            // Assert
            TimerScheduledEvent scheduledEvent = (TimerScheduledEvent) events.getFirst();
            // The duration should be approximately 2 hours (allowing for test execution time)
            assertThat(scheduledEvent.duration()).isPositive();
            assertThat(scheduledEvent.duration()).isCloseTo(Duration.ofHours(2), Duration.ofSeconds(5));

            assertThat(timerService.scheduleCalls).hasSize(1);
            assertThat(timerService.scheduleCalls.getFirst().duration()).isPositive();
        }

        @Test
        @DisplayName("Should schedule timer with Duration.ZERO for past date")
        void shouldScheduleWithZeroDurationForPastDate() {
            // Arrange
            Instant pastDate = Instant.now().minus(Duration.ofHours(1));
            TimerDefinition timerDef = new TimerDefinition(
                    TimerDefinition.TimerType.DATE, pastDate.toString());
            TimerIntermediateCatchEvent timerEvent = new TimerIntermediateCatchEvent(
                    "timer1", "Past date timer",
                    List.of("flow_to_timer"), List.of("flow_from_timer"), timerDef);
            ProcessDefinition definition = createMinimalDefinition(timerEvent);
            Token token = Token.create("timer1");
            ExecutionContext context = createContext(UUID.randomUUID(), definition, token);

            // Act
            List<ProcessEvent> events = handler.handle(token, timerEvent, context);

            // Assert
            TimerScheduledEvent scheduledEvent = (TimerScheduledEvent) events.getFirst();
            assertThat(scheduledEvent.duration()).isEqualTo(Duration.ZERO);

            assertThat(timerService.scheduleCalls).hasSize(1);
            assertThat(timerService.scheduleCalls.getFirst().duration()).isEqualTo(Duration.ZERO);
        }
    }

    @Nested
    @DisplayName("CYCLE type timer")
    class CycleTypeTests {

        @Test
        @DisplayName("Should schedule timer using the cycle interval")
        void shouldScheduleTimerWithCycleInterval() {
            // Arrange
            TimerDefinition timerDef = new TimerDefinition(TimerDefinition.TimerType.CYCLE, "R3/PT10H");
            TimerIntermediateCatchEvent timerEvent = new TimerIntermediateCatchEvent(
                    "timer1", "Repeat 3 times every 10 hours",
                    List.of("flow_to_timer"), List.of("flow_from_timer"), timerDef);
            ProcessDefinition definition = createMinimalDefinition(timerEvent);
            Token token = Token.create("timer1");
            UUID processInstanceId = UUID.randomUUID();
            ExecutionContext context = createContext(processInstanceId, definition, token);

            // Act
            List<ProcessEvent> events = handler.handle(token, timerEvent, context);

            // Assert
            TimerScheduledEvent scheduledEvent = (TimerScheduledEvent) events.getFirst();
            assertThat(scheduledEvent.duration()).isEqualTo(Duration.ofHours(10));
            assertThat(scheduledEvent.processInstanceId()).isEqualTo(processInstanceId);
            assertThat(scheduledEvent.nodeId()).isEqualTo("timer1");

            assertThat(timerService.scheduleCalls).hasSize(1);
            assertThat(timerService.scheduleCalls.getFirst().duration()).isEqualTo(Duration.ofHours(10));
        }

        @Test
        @DisplayName("Should schedule timer with infinite cycle interval")
        void shouldScheduleTimerWithInfiniteCycleInterval() {
            // Arrange
            TimerDefinition timerDef = new TimerDefinition(TimerDefinition.TimerType.CYCLE, "R/PT5M");
            TimerIntermediateCatchEvent timerEvent = new TimerIntermediateCatchEvent(
                    "timer1", "Repeat infinitely every 5 minutes",
                    List.of("flow_to_timer"), List.of("flow_from_timer"), timerDef);
            ProcessDefinition definition = createMinimalDefinition(timerEvent);
            Token token = Token.create("timer1");
            ExecutionContext context = createContext(UUID.randomUUID(), definition, token);

            // Act
            List<ProcessEvent> events = handler.handle(token, timerEvent, context);

            // Assert
            TimerScheduledEvent scheduledEvent = (TimerScheduledEvent) events.getFirst();
            assertThat(scheduledEvent.duration()).isEqualTo(Duration.ofMinutes(5));
        }
    }

    @Nested
    @DisplayName("Common behavior")
    class CommonBehaviorTests {

        @Test
        @DisplayName("Should always return exactly one TimerScheduledEvent")
        void shouldReturnExactlyOneEvent() {
            // Arrange
            TimerDefinition timerDef = new TimerDefinition(TimerDefinition.TimerType.DURATION, "PT1H");
            TimerIntermediateCatchEvent timerEvent = new TimerIntermediateCatchEvent(
                    "timer1", "1 hour timer",
                    List.of("flow_to_timer"), List.of("flow_from_timer"), timerDef);
            ProcessDefinition definition = createMinimalDefinition(timerEvent);
            Token token = Token.create("timer1");
            ExecutionContext context = createContext(UUID.randomUUID(), definition, token);

            // Act
            List<ProcessEvent> events = handler.handle(token, timerEvent, context);

            // Assert
            assertThat(events)
                    .hasSize(1)
                    .allSatisfy(event -> assertThat(event).isInstanceOf(TimerScheduledEvent.class));
        }

        @Test
        @DisplayName("Should set occurredAt timestamp on the event")
        void shouldSetOccurredAtTimestamp() {
            // Arrange
            Instant before = Instant.now();
            TimerDefinition timerDef = new TimerDefinition(TimerDefinition.TimerType.DURATION, "PT10M");
            TimerIntermediateCatchEvent timerEvent = new TimerIntermediateCatchEvent(
                    "timer1", "10 min timer",
                    List.of("flow_to_timer"), List.of("flow_from_timer"), timerDef);
            ProcessDefinition definition = createMinimalDefinition(timerEvent);
            Token token = Token.create("timer1");
            ExecutionContext context = createContext(UUID.randomUUID(), definition, token);

            // Act
            List<ProcessEvent> events = handler.handle(token, timerEvent, context);

            // Assert
            Instant after = Instant.now();
            TimerScheduledEvent scheduledEvent = (TimerScheduledEvent) events.getFirst();
            assertThat(scheduledEvent.occurredAt())
                    .isAfterOrEqualTo(before)
                    .isBeforeOrEqualTo(after);
        }

        @Test
        @DisplayName("Should assign non-null event ID")
        void shouldAssignNonNullEventId() {
            // Arrange
            TimerDefinition timerDef = new TimerDefinition(TimerDefinition.TimerType.DURATION, "PT1M");
            TimerIntermediateCatchEvent timerEvent = new TimerIntermediateCatchEvent(
                    "timer1", "1 min timer",
                    List.of("flow_to_timer"), List.of("flow_from_timer"), timerDef);
            ProcessDefinition definition = createMinimalDefinition(timerEvent);
            Token token = Token.create("timer1");
            ExecutionContext context = createContext(UUID.randomUUID(), definition, token);

            // Act
            List<ProcessEvent> events = handler.handle(token, timerEvent, context);

            // Assert
            TimerScheduledEvent scheduledEvent = (TimerScheduledEvent) events.getFirst();
            assertThat(scheduledEvent.id()).isNotNull();
        }
    }
}
