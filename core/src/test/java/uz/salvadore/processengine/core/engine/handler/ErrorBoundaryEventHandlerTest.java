package uz.salvadore.processengine.core.engine.handler;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uz.salvadore.processengine.core.domain.enums.ProcessState;
import uz.salvadore.processengine.core.domain.event.ProcessEvent;
import uz.salvadore.processengine.core.domain.event.TokenMovedEvent;
import uz.salvadore.processengine.core.domain.model.EndEvent;
import uz.salvadore.processengine.core.domain.model.ErrorBoundaryEvent;
import uz.salvadore.processengine.core.domain.model.ProcessDefinition;
import uz.salvadore.processengine.core.domain.model.ProcessInstance;
import uz.salvadore.processengine.core.domain.model.SequenceFlow;
import uz.salvadore.processengine.core.domain.model.StartEvent;
import uz.salvadore.processengine.core.domain.model.Token;
import uz.salvadore.processengine.core.engine.context.ExecutionContext;
import uz.salvadore.processengine.core.adapter.inmemory.InMemorySequenceGenerator;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ErrorBoundaryEventHandlerTest {

    private final InMemorySequenceGenerator sequenceGenerator = new InMemorySequenceGenerator();
    private final ErrorBoundaryEventHandler handler = new ErrorBoundaryEventHandler(sequenceGenerator);

    @Test
    @DisplayName("Should emit TokenMovedEvent to outgoing flow target")
    void shouldEmitTokenMovedEventToOutgoingFlowTarget() {
        // Arrange
        ErrorBoundaryEvent errorBoundary = new ErrorBoundaryEvent("error1", "Payment Failed",
                List.of(), List.of("flow_error"), "CallActivity_Payment", "PAYMENT_FAILED", true);
        StartEvent startEvent = new StartEvent("start1", "Start", List.of(), List.of("flow1"));
        EndEvent endEvent = new EndEvent("end1", "End", List.of("flow_error"), List.of(), null);
        SequenceFlow flow1 = new SequenceFlow("flow1", "start1", "error1", null);
        SequenceFlow flowError = new SequenceFlow("flow_error", "error1", "notifyTask", null);

        ProcessDefinition definition = ProcessDefinition.create("test", 1, "Test", "<xml/>",
                List.of(startEvent, errorBoundary, endEvent), List.of(flow1, flowError));

        Token token = Token.create("error1");
        UUID processInstanceId = UUID.randomUUID();
        ProcessInstance instance = ProcessInstance.restore(
                processInstanceId, definition.getId(), null, null, ProcessState.RUNNING,
                List.of(token), Map.of(), Instant.now(), null
        );
        ExecutionContext context = new ExecutionContext(instance, definition);

        // Act
        List<ProcessEvent> events = handler.handle(token, errorBoundary, context);

        // Assert
        assertThat(events).hasSize(1);
        assertThat(events.getFirst()).isInstanceOf(TokenMovedEvent.class);

        TokenMovedEvent movedEvent = (TokenMovedEvent) events.getFirst();
        assertThat(movedEvent.processInstanceId()).isEqualTo(processInstanceId);
        assertThat(movedEvent.tokenId()).isEqualTo(token.getId());
        assertThat(movedEvent.fromNodeId()).isEqualTo("error1");
        assertThat(movedEvent.toNodeId()).isEqualTo("notifyTask");
    }

    @Test
    @DisplayName("Should throw when ErrorBoundaryEvent has no outgoing flows")
    void shouldThrowWhenNoOutgoingFlows() {
        // Arrange
        ErrorBoundaryEvent errorBoundary = new ErrorBoundaryEvent("error1", "Error",
                List.of(), List.of(), "someTask", "ERR_CODE", true);
        StartEvent startEvent = new StartEvent("start1", "Start", List.of(), List.of("flow1"));
        EndEvent endEvent = new EndEvent("end1", "End", List.of(), List.of(), null);
        SequenceFlow flow1 = new SequenceFlow("flow1", "start1", "error1", null);

        ProcessDefinition definition = ProcessDefinition.create("test", 1, "Test", "<xml/>",
                List.of(startEvent, errorBoundary, endEvent), List.of(flow1));

        Token token = Token.create("error1");
        ProcessInstance instance = ProcessInstance.restore(
                UUID.randomUUID(), definition.getId(), null, null, ProcessState.RUNNING,
                List.of(token), Map.of(), Instant.now(), null
        );
        ExecutionContext context = new ExecutionContext(instance, definition);

        // Act & Assert
        assertThatThrownBy(() -> handler.handle(token, errorBoundary, context))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ErrorBoundaryEvent 'error1' has no outgoing flows");
    }
}
