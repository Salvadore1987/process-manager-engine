package uz.salvadore.processengine.core.engine.handler;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uz.salvadore.processengine.core.domain.enums.ProcessState;
import uz.salvadore.processengine.core.domain.event.ProcessEvent;
import uz.salvadore.processengine.core.domain.event.TimerScheduledEvent;
import uz.salvadore.processengine.core.domain.model.EndEvent;
import uz.salvadore.processengine.core.domain.model.ProcessDefinition;
import uz.salvadore.processengine.core.domain.model.ProcessInstance;
import uz.salvadore.processengine.core.domain.model.SequenceFlow;
import uz.salvadore.processengine.core.domain.model.StartEvent;
import uz.salvadore.processengine.core.domain.model.TimerBoundaryEvent;
import uz.salvadore.processengine.core.domain.model.TimerDefinition;
import uz.salvadore.processengine.core.domain.model.Token;
import uz.salvadore.processengine.core.engine.context.ExecutionContext;
import uz.salvadore.processengine.core.adapter.inmemory.InMemorySequenceGenerator;
import uz.salvadore.processengine.core.port.outgoing.TimerService;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

class TimerBoundaryEventHandlerTest {

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
    private final TimerBoundaryEventHandler handler = new TimerBoundaryEventHandler(timerService, sequenceGenerator);

    @Test
    @DisplayName("Should call timerService.schedule() and emit TimerScheduledEvent")
    void shouldScheduleTimerAndEmitEvent() {
        // Arrange
        TimerBoundaryEvent timerEvent = new TimerBoundaryEvent("timer1", "Stock Timeout 30m",
                List.of(), List.of("flow_timeout"), "Task_ReserveStock",
                new TimerDefinition(TimerDefinition.TimerType.DURATION, "PT30M"), true);
        StartEvent startEvent = new StartEvent("start1", "Start", List.of(), List.of("flow1"));
        EndEvent endEvent = new EndEvent("end1", "End", List.of("flow_timeout"), List.of(), null);
        SequenceFlow flow1 = new SequenceFlow("flow1", "start1", "timer1", null);
        SequenceFlow flowTimeout = new SequenceFlow("flow_timeout", "timer1", "end1", null);

        ProcessDefinition definition = ProcessDefinition.create("test", 1, "Test", "<xml/>",
                List.of(startEvent, timerEvent, endEvent), List.of(flow1, flowTimeout));

        Token token = Token.create("timer1");
        UUID processInstanceId = UUID.randomUUID();
        ProcessInstance instance = ProcessInstance.restore(
                processInstanceId, definition.getId(), null, ProcessState.RUNNING,
                List.of(token), Map.of(), Instant.now(), null
        );
        ExecutionContext context = new ExecutionContext(instance, definition);

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

        assertThat(timerService.scheduleCalls).hasSize(1);
        RecordingTimerService.ScheduleCall scheduleCall = timerService.scheduleCalls.getFirst();
        assertThat(scheduleCall.processInstanceId()).isEqualTo(processInstanceId);
        assertThat(scheduleCall.tokenId()).isEqualTo(token.getId());
        assertThat(scheduleCall.nodeId()).isEqualTo("timer1");
        assertThat(scheduleCall.duration()).isEqualTo(Duration.ofMinutes(30));
    }

    @Test
    @DisplayName("Should handle different durations correctly")
    void shouldHandleDifferentDurations() {
        // Arrange
        TimerBoundaryEvent timerEvent = new TimerBoundaryEvent("timer1", "Quick Timer",
                List.of(), List.of("flow_out"), "someTask",
                new TimerDefinition(TimerDefinition.TimerType.DURATION, "PT5S"), false);
        StartEvent startEvent = new StartEvent("start1", "Start", List.of(), List.of("flow1"));
        EndEvent endEvent = new EndEvent("end1", "End", List.of("flow_out"), List.of(), null);
        SequenceFlow flow1 = new SequenceFlow("flow1", "start1", "timer1", null);
        SequenceFlow flowOut = new SequenceFlow("flow_out", "timer1", "end1", null);

        ProcessDefinition definition = ProcessDefinition.create("test", 1, "Test", "<xml/>",
                List.of(startEvent, timerEvent, endEvent), List.of(flow1, flowOut));

        Token token = Token.create("timer1");
        ProcessInstance instance = ProcessInstance.restore(
                UUID.randomUUID(), definition.getId(), null, ProcessState.RUNNING,
                List.of(token), Map.of(), Instant.now(), null
        );
        ExecutionContext context = new ExecutionContext(instance, definition);

        // Act
        List<ProcessEvent> events = handler.handle(token, timerEvent, context);

        // Assert
        TimerScheduledEvent scheduledEvent = (TimerScheduledEvent) events.getFirst();
        assertThat(scheduledEvent.duration()).isEqualTo(Duration.ofSeconds(5));
    }
}
