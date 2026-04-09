package uz.salvadore.processengine.core.engine.handler;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uz.salvadore.processengine.core.domain.enums.ProcessState;
import uz.salvadore.processengine.core.domain.event.ProcessEvent;
import uz.salvadore.processengine.core.domain.model.EndEvent;
import uz.salvadore.processengine.core.domain.model.ProcessDefinition;
import uz.salvadore.processengine.core.domain.model.ProcessInstance;
import uz.salvadore.processengine.core.domain.model.SequenceFlow;
import uz.salvadore.processengine.core.domain.model.ServiceTask;
import uz.salvadore.processengine.core.domain.model.StartEvent;
import uz.salvadore.processengine.core.domain.model.Token;
import uz.salvadore.processengine.core.engine.context.ExecutionContext;
import uz.salvadore.processengine.core.port.outgoing.MessageTransport;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

class ServiceTaskHandlerTest {

    static class RecordingMessageTransport implements MessageTransport {
        final List<SendCall> sendCalls = new ArrayList<>();

        record SendCall(String topic, UUID correlationId, Map<String, Object> payload) {}

        @Override
        public void send(String topic, UUID correlationId, Map<String, Object> payload) {
            sendCalls.add(new SendCall(topic, correlationId, payload));
        }

        @Override
        public void subscribe(String topic, Consumer<MessageResult> callback) {
            // no-op for tests
        }
    }

    private final RecordingMessageTransport messageTransport = new RecordingMessageTransport();
    private final ServiceTaskHandler handler = new ServiceTaskHandler(messageTransport);

    @Test
    @DisplayName("Should send message via MessageTransport with correct topic and correlationId")
    void shouldSendMessageViaMessageTransport() {
        // Arrange
        ServiceTask serviceTask = new ServiceTask("task1", "Validate Order",
                List.of("flow1"), List.of("flow2"), "order.validate", 3, Duration.ofSeconds(10));
        StartEvent startEvent = new StartEvent("start1", "Start", List.of(), List.of("flow1"));
        EndEvent endEvent = new EndEvent("end1", "End", List.of("flow2"), List.of(), null);
        SequenceFlow flow1 = new SequenceFlow("flow1", "start1", "task1", null);
        SequenceFlow flow2 = new SequenceFlow("flow2", "task1", "end1", null);

        ProcessDefinition definition = ProcessDefinition.create("test", 1, "Test", "<xml/>",
                List.of(startEvent, serviceTask, endEvent), List.of(flow1, flow2));

        Token token = Token.create("task1");
        Map<String, Object> variables = Map.of("orderId", "ORD-123", "amount", 5000L);
        ProcessInstance instance = ProcessInstance.restore(
                UUID.randomUUID(), definition.getId(), null, null, ProcessState.RUNNING,
                List.of(token), variables, Instant.now(), null
        );
        ExecutionContext context = new ExecutionContext(instance, definition);

        // Act
        List<ProcessEvent> events = handler.handle(token, serviceTask, context);

        // Assert
        assertThat(events).isEmpty();
        assertThat(messageTransport.sendCalls).hasSize(1);

        RecordingMessageTransport.SendCall sendCall = messageTransport.sendCalls.getFirst();
        assertThat(sendCall.topic()).isEqualTo("order.validate");
        assertThat(sendCall.correlationId()).isEqualTo(token.getId());
        assertThat(sendCall.payload()).containsEntry("orderId", "ORD-123");
        assertThat(sendCall.payload()).containsEntry("amount", 5000L);
    }

    @Test
    @DisplayName("Should return empty event list (token waits for external completion)")
    void shouldReturnEmptyEventList() {
        // Arrange
        ServiceTask serviceTask = new ServiceTask("task1", "Task",
                List.of("flow1"), List.of("flow2"), "some.topic", 0, Duration.ZERO);
        StartEvent startEvent = new StartEvent("start1", "Start", List.of(), List.of("flow1"));
        EndEvent endEvent = new EndEvent("end1", "End", List.of("flow2"), List.of(), null);
        SequenceFlow flow1 = new SequenceFlow("flow1", "start1", "task1", null);
        SequenceFlow flow2 = new SequenceFlow("flow2", "task1", "end1", null);

        ProcessDefinition definition = ProcessDefinition.create("test", 1, "Test", "<xml/>",
                List.of(startEvent, serviceTask, endEvent), List.of(flow1, flow2));

        Token token = Token.create("task1");
        ProcessInstance instance = ProcessInstance.restore(
                UUID.randomUUID(), definition.getId(), null, null, ProcessState.RUNNING,
                List.of(token), Map.of(), Instant.now(), null
        );
        ExecutionContext context = new ExecutionContext(instance, definition);

        // Act
        List<ProcessEvent> events = handler.handle(token, serviceTask, context);

        // Assert
        assertThat(events).isEmpty();
    }

    @Test
    @DisplayName("Should send message with empty variables when instance has no variables")
    void shouldSendMessageWithEmptyVariables() {
        // Arrange
        ServiceTask serviceTask = new ServiceTask("task1", "Task",
                List.of("flow1"), List.of("flow2"), "order.validate", 0, Duration.ZERO);
        StartEvent startEvent = new StartEvent("start1", "Start", List.of(), List.of("flow1"));
        EndEvent endEvent = new EndEvent("end1", "End", List.of("flow2"), List.of(), null);
        SequenceFlow flow1 = new SequenceFlow("flow1", "start1", "task1", null);
        SequenceFlow flow2 = new SequenceFlow("flow2", "task1", "end1", null);

        ProcessDefinition definition = ProcessDefinition.create("test", 1, "Test", "<xml/>",
                List.of(startEvent, serviceTask, endEvent), List.of(flow1, flow2));

        Token token = Token.create("task1");
        ProcessInstance instance = ProcessInstance.restore(
                UUID.randomUUID(), definition.getId(), null, null, ProcessState.RUNNING,
                List.of(token), Map.of(), Instant.now(), null
        );
        ExecutionContext context = new ExecutionContext(instance, definition);

        // Act
        handler.handle(token, serviceTask, context);

        // Assert
        assertThat(messageTransport.sendCalls.getFirst().payload()).isEmpty();
    }
}
