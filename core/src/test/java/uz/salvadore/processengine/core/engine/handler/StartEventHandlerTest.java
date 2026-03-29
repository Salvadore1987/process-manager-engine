package uz.salvadore.processengine.core.engine.handler;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uz.salvadore.processengine.core.domain.enums.ProcessState;
import uz.salvadore.processengine.core.domain.event.ProcessEvent;
import uz.salvadore.processengine.core.domain.event.TokenMovedEvent;
import uz.salvadore.processengine.core.domain.model.ProcessDefinition;
import uz.salvadore.processengine.core.domain.model.ProcessInstance;
import uz.salvadore.processengine.core.domain.model.EndEvent;
import uz.salvadore.processengine.core.domain.model.SequenceFlow;
import uz.salvadore.processengine.core.domain.model.StartEvent;
import uz.salvadore.processengine.core.domain.model.Token;
import uz.salvadore.processengine.core.engine.context.ExecutionContext;
import uz.salvadore.processengine.core.engine.eventsourcing.EventSequencer;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StartEventHandlerTest {

    private final EventSequencer eventSequencer = new EventSequencer();
    private final StartEventHandler handler = new StartEventHandler(eventSequencer);

    @Test
    @DisplayName("Should emit TokenMovedEvent to first outgoing flow target")
    void shouldEmitTokenMovedEventToFirstOutgoingFlowTarget() {
        // Arrange
        StartEvent startEvent = new StartEvent("start1", "Start", List.of(), List.of("flow1"));
        EndEvent endEvent = new EndEvent("end1", "End", List.of("flow1"), List.of(), null);
        SequenceFlow flow = new SequenceFlow("flow1", "start1", "task1", null);

        ProcessDefinition definition = ProcessDefinition.create(
                "test-process", 1, "Test", "<xml/>",
                List.of(startEvent, endEvent),
                List.of(flow)
        );

        UUID processInstanceId = UUID.randomUUID();
        Token token = Token.create("start1");
        ProcessInstance instance = ProcessInstance.restore(
                processInstanceId, definition.getId(), null, ProcessState.RUNNING,
                List.of(token), Map.of(), Instant.now(), null
        );

        ExecutionContext context = new ExecutionContext(instance, definition);

        // Act
        List<ProcessEvent> events = handler.handle(token, startEvent, context);

        // Assert
        assertThat(events).hasSize(1);
        assertThat(events.getFirst()).isInstanceOf(TokenMovedEvent.class);

        TokenMovedEvent movedEvent = (TokenMovedEvent) events.getFirst();
        assertThat(movedEvent.processInstanceId()).isEqualTo(processInstanceId);
        assertThat(movedEvent.tokenId()).isEqualTo(token.getId());
        assertThat(movedEvent.fromNodeId()).isEqualTo("start1");
        assertThat(movedEvent.toNodeId()).isEqualTo("task1");
        assertThat(movedEvent.sequenceNumber()).isEqualTo(1L);
    }

    @Test
    @DisplayName("Should throw when StartEvent has no outgoing flows")
    void shouldThrowWhenStartEventHasNoOutgoingFlows() {
        // Arrange
        StartEvent startEvent = new StartEvent("start1", "Start", List.of(), List.of());
        EndEvent endEvent = new EndEvent("end1", "End", List.of(), List.of(), null);

        ProcessDefinition definition = ProcessDefinition.create(
                "test-process", 1, "Test", "<xml/>",
                List.of(startEvent, endEvent),
                List.of()
        );

        Token token = Token.create("start1");
        ProcessInstance instance = ProcessInstance.restore(
                UUID.randomUUID(), definition.getId(), null, ProcessState.RUNNING,
                List.of(token), Map.of(), Instant.now(), null
        );

        ExecutionContext context = new ExecutionContext(instance, definition);

        // Act & Assert
        assertThatThrownBy(() -> handler.handle(token, startEvent, context))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("StartEvent 'start1' has no outgoing flows");
    }
}
