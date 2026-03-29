package uz.salvadore.processengine.core.engine.handler;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uz.salvadore.processengine.core.domain.enums.ProcessState;
import uz.salvadore.processengine.core.domain.event.CallActivityStartedEvent;
import uz.salvadore.processengine.core.domain.event.ProcessEvent;
import uz.salvadore.processengine.core.domain.model.CallActivity;
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

class CallActivityHandlerTest {

    private final EventSequencer eventSequencer = new EventSequencer();
    private final CallActivityHandler handler = new CallActivityHandler(eventSequencer);

    @Test
    @DisplayName("Should emit CallActivityStartedEvent with childProcessInstanceId and calledElement")
    void shouldEmitCallActivityStartedEvent() {
        // Arrange
        CallActivity callActivity = new CallActivity("call1", "Payment Processing",
                List.of("flow_in"), List.of("flow_out"), "payment-processing");
        StartEvent startEvent = new StartEvent("start1", "Start", List.of(), List.of("flow_in"));
        EndEvent endEvent = new EndEvent("end1", "End", List.of("flow_out"), List.of(), null);
        SequenceFlow flowIn = new SequenceFlow("flow_in", "start1", "call1", null);
        SequenceFlow flowOut = new SequenceFlow("flow_out", "call1", "end1", null);

        ProcessDefinition definition = ProcessDefinition.create("test", 1, "Test", "<xml/>",
                List.of(startEvent, callActivity, endEvent), List.of(flowIn, flowOut));

        Token token = Token.create("call1");
        UUID processInstanceId = UUID.randomUUID();
        ProcessInstance instance = ProcessInstance.restore(
                processInstanceId, definition.getId(), null, ProcessState.RUNNING,
                List.of(token), Map.of(), Instant.now(), null
        );
        ExecutionContext context = new ExecutionContext(instance, definition);

        // Act
        List<ProcessEvent> events = handler.handle(token, callActivity, context);

        // Assert
        assertThat(events).hasSize(1);
        assertThat(events.getFirst()).isInstanceOf(CallActivityStartedEvent.class);

        CallActivityStartedEvent startedEvent = (CallActivityStartedEvent) events.getFirst();
        assertThat(startedEvent.processInstanceId()).isEqualTo(processInstanceId);
        assertThat(startedEvent.tokenId()).isEqualTo(token.getId());
        assertThat(startedEvent.nodeId()).isEqualTo("call1");
        assertThat(startedEvent.calledElement()).isEqualTo("payment-processing");
        assertThat(startedEvent.childProcessInstanceId()).isNotNull();
        assertThat(startedEvent.sequenceNumber()).isEqualTo(1L);
    }

    @Test
    @DisplayName("Should generate unique childProcessInstanceId each time")
    void shouldGenerateUniqueChildProcessInstanceId() {
        // Arrange
        CallActivity callActivity = new CallActivity("call1", "Payment",
                List.of("flow_in"), List.of("flow_out"), "payment-processing");
        StartEvent startEvent = new StartEvent("start1", "Start", List.of(), List.of("flow_in"));
        EndEvent endEvent = new EndEvent("end1", "End", List.of("flow_out"), List.of(), null);
        SequenceFlow flowIn = new SequenceFlow("flow_in", "start1", "call1", null);
        SequenceFlow flowOut = new SequenceFlow("flow_out", "call1", "end1", null);

        ProcessDefinition definition = ProcessDefinition.create("test", 1, "Test", "<xml/>",
                List.of(startEvent, callActivity, endEvent), List.of(flowIn, flowOut));

        Token token1 = Token.create("call1");
        Token token2 = Token.create("call1");
        ProcessInstance instance1 = ProcessInstance.restore(
                UUID.randomUUID(), definition.getId(), null, ProcessState.RUNNING,
                List.of(token1), Map.of(), Instant.now(), null
        );
        ProcessInstance instance2 = ProcessInstance.restore(
                UUID.randomUUID(), definition.getId(), null, ProcessState.RUNNING,
                List.of(token2), Map.of(), Instant.now(), null
        );

        // Act
        List<ProcessEvent> events1 = handler.handle(token1, callActivity, new ExecutionContext(instance1, definition));
        List<ProcessEvent> events2 = handler.handle(token2, callActivity, new ExecutionContext(instance2, definition));

        // Assert
        CallActivityStartedEvent event1 = (CallActivityStartedEvent) events1.getFirst();
        CallActivityStartedEvent event2 = (CallActivityStartedEvent) events2.getFirst();
        assertThat(event1.childProcessInstanceId()).isNotEqualTo(event2.childProcessInstanceId());
    }
}
