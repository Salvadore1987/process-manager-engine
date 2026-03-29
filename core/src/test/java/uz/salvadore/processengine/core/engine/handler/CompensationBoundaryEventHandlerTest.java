package uz.salvadore.processengine.core.engine.handler;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uz.salvadore.processengine.core.domain.enums.ProcessState;
import uz.salvadore.processengine.core.domain.event.CompensationTriggeredEvent;
import uz.salvadore.processengine.core.domain.event.ProcessEvent;
import uz.salvadore.processengine.core.domain.model.CompensationBoundaryEvent;
import uz.salvadore.processengine.core.domain.model.EndEvent;
import uz.salvadore.processengine.core.domain.model.ProcessDefinition;
import uz.salvadore.processengine.core.domain.model.ProcessInstance;
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

class CompensationBoundaryEventHandlerTest {

    private final EventSequencer eventSequencer = new EventSequencer();
    private final CompensationBoundaryEventHandler handler = new CompensationBoundaryEventHandler(eventSequencer);

    @Test
    @DisplayName("Should emit CompensationTriggeredEvent with attachedToRef and compensation task id")
    void shouldEmitCompensationTriggeredEvent() {
        // Arrange
        CompensationBoundaryEvent compensationEvent = new CompensationBoundaryEvent(
                "comp1", "Compensate Payment", List.of(), List.of("flow_comp"),
                "CallActivity_Payment");
        StartEvent startEvent = new StartEvent("start1", "Start", List.of(), List.of("flow1"));
        EndEvent endEvent = new EndEvent("end1", "End", List.of("flow_comp"), List.of(), null);
        SequenceFlow flow1 = new SequenceFlow("flow1", "start1", "comp1", null);
        SequenceFlow flowComp = new SequenceFlow("flow_comp", "comp1", "Task_RefundPayment", null);

        ProcessDefinition definition = ProcessDefinition.create("test", 1, "Test", "<xml/>",
                List.of(startEvent, compensationEvent, endEvent), List.of(flow1, flowComp));

        Token token = Token.create("comp1");
        UUID processInstanceId = UUID.randomUUID();
        ProcessInstance instance = ProcessInstance.restore(
                processInstanceId, definition.getId(), null, ProcessState.RUNNING,
                List.of(token), Map.of(), Instant.now(), null
        );
        ExecutionContext context = new ExecutionContext(instance, definition);

        // Act
        List<ProcessEvent> events = handler.handle(token, compensationEvent, context);

        // Assert
        assertThat(events).hasSize(1);
        assertThat(events.getFirst()).isInstanceOf(CompensationTriggeredEvent.class);

        CompensationTriggeredEvent triggeredEvent = (CompensationTriggeredEvent) events.getFirst();
        assertThat(triggeredEvent.processInstanceId()).isEqualTo(processInstanceId);
        assertThat(triggeredEvent.sourceNodeId()).isEqualTo("CallActivity_Payment");
        assertThat(triggeredEvent.compensationTaskId()).isEqualTo("Task_RefundPayment");
        assertThat(triggeredEvent.sequenceNumber()).isEqualTo(1L);
    }

    @Test
    @DisplayName("Should set compensationTaskId to null when no outgoing flows")
    void shouldSetCompensationTaskIdToNullWhenNoOutgoingFlows() {
        // Arrange
        CompensationBoundaryEvent compensationEvent = new CompensationBoundaryEvent(
                "comp1", "Compensate", List.of(), List.of(), "someTask");
        StartEvent startEvent = new StartEvent("start1", "Start", List.of(), List.of("flow1"));
        EndEvent endEvent = new EndEvent("end1", "End", List.of(), List.of(), null);
        SequenceFlow flow1 = new SequenceFlow("flow1", "start1", "comp1", null);

        ProcessDefinition definition = ProcessDefinition.create("test", 1, "Test", "<xml/>",
                List.of(startEvent, compensationEvent, endEvent), List.of(flow1));

        Token token = Token.create("comp1");
        ProcessInstance instance = ProcessInstance.restore(
                UUID.randomUUID(), definition.getId(), null, ProcessState.RUNNING,
                List.of(token), Map.of(), Instant.now(), null
        );
        ExecutionContext context = new ExecutionContext(instance, definition);

        // Act
        List<ProcessEvent> events = handler.handle(token, compensationEvent, context);

        // Assert
        CompensationTriggeredEvent triggeredEvent = (CompensationTriggeredEvent) events.getFirst();
        assertThat(triggeredEvent.compensationTaskId()).isNull();
        assertThat(triggeredEvent.sourceNodeId()).isEqualTo("someTask");
    }
}
