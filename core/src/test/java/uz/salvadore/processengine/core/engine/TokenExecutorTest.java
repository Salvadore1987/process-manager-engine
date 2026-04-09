package uz.salvadore.processengine.core.engine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uz.salvadore.processengine.core.domain.enums.NodeType;
import uz.salvadore.processengine.core.domain.enums.ProcessState;
import uz.salvadore.processengine.core.domain.event.ProcessEvent;
import uz.salvadore.processengine.core.domain.event.TokenMovedEvent;
import uz.salvadore.processengine.core.domain.model.EndEvent;
import uz.salvadore.processengine.core.domain.model.FlowNode;
import uz.salvadore.processengine.core.domain.model.ProcessDefinition;
import uz.salvadore.processengine.core.domain.model.ProcessInstance;
import uz.salvadore.processengine.core.domain.model.SequenceFlow;
import uz.salvadore.processengine.core.domain.model.ServiceTask;
import uz.salvadore.processengine.core.domain.model.StartEvent;
import uz.salvadore.processengine.core.domain.model.Token;
import uz.salvadore.processengine.core.engine.context.ExecutionContext;
import uz.salvadore.processengine.core.engine.handler.NodeHandler;
import uz.salvadore.processengine.core.util.UUIDv7;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TokenExecutorTest {

    @Test
    @DisplayName("Should dispatch to correct handler based on node type")
    void shouldDispatchToCorrectHandler() {
        // Arrange
        NodeHandler startHandler = (token, node, context) -> {
            TokenMovedEvent event = new TokenMovedEvent(
                    UUIDv7.generate(), context.getProcessInstance().getId(),
                    token.getId(), node.id(), "task1", Instant.now(), 1L
            );
            return List.of(event);
        };

        TokenExecutor executor = new TokenExecutor(Map.of(NodeType.START_EVENT, startHandler));

        StartEvent startEvent = new StartEvent("start1", "Start", List.of(), List.of("flow1"));
        EndEvent endEvent = new EndEvent("end1", "End", List.of("flow1"), List.of(), null);
        SequenceFlow flow = new SequenceFlow("flow1", "start1", "end1", null);
        ProcessDefinition definition = ProcessDefinition.create("test", 1, "Test", "<xml/>",
                List.of(startEvent, endEvent), List.of(flow));

        Token token = Token.create("start1");
        ProcessInstance instance = ProcessInstance.restore(
                UUID.randomUUID(), definition.getId(), null, null, ProcessState.RUNNING,
                List.of(token), Map.of(), Instant.now(), null
        );
        ExecutionContext context = new ExecutionContext(instance, definition);

        // Act
        List<ProcessEvent> events = executor.execute(token, startEvent, context);

        // Assert
        assertThat(events).hasSize(1);
        assertThat(events.getFirst()).isInstanceOf(TokenMovedEvent.class);
    }

    @Test
    @DisplayName("Should throw IllegalStateException if no handler for node type")
    void shouldThrowWhenNoHandlerRegistered() {
        // Arrange
        TokenExecutor executor = new TokenExecutor(Map.of());

        StartEvent startEvent = new StartEvent("start1", "Start", List.of(), List.of("flow1"));
        EndEvent endEvent = new EndEvent("end1", "End", List.of("flow1"), List.of(), null);
        SequenceFlow flow = new SequenceFlow("flow1", "start1", "end1", null);
        ProcessDefinition definition = ProcessDefinition.create("test", 1, "Test", "<xml/>",
                List.of(startEvent, endEvent), List.of(flow));

        Token token = Token.create("start1");
        ProcessInstance instance = ProcessInstance.restore(
                UUID.randomUUID(), definition.getId(), null, null, ProcessState.RUNNING,
                List.of(token), Map.of(), Instant.now(), null
        );
        ExecutionContext context = new ExecutionContext(instance, definition);

        // Act & Assert
        assertThatThrownBy(() -> executor.execute(token, startEvent, context))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No handler registered for node type: START_EVENT");
    }

    @Test
    @DisplayName("Should dispatch ServiceTask to SERVICE_TASK handler")
    void shouldDispatchServiceTaskToCorrectHandler() {
        // Arrange
        NodeHandler serviceHandler = (token, node, context) -> List.of();

        TokenExecutor executor = new TokenExecutor(Map.of(NodeType.SERVICE_TASK, serviceHandler));

        ServiceTask serviceTask = new ServiceTask("task1", "Task",
                List.of("flow_in"), List.of("flow_out"), "my.topic", 0, Duration.ZERO);
        StartEvent startEvent = new StartEvent("start1", "Start", List.of(), List.of("flow_in"));
        EndEvent endEvent = new EndEvent("end1", "End", List.of("flow_out"), List.of(), null);
        SequenceFlow flowIn = new SequenceFlow("flow_in", "start1", "task1", null);
        SequenceFlow flowOut = new SequenceFlow("flow_out", "task1", "end1", null);

        ProcessDefinition definition = ProcessDefinition.create("test", 1, "Test", "<xml/>",
                List.of(startEvent, serviceTask, endEvent), List.of(flowIn, flowOut));

        Token token = Token.create("task1");
        ProcessInstance instance = ProcessInstance.restore(
                UUID.randomUUID(), definition.getId(), null, null, ProcessState.RUNNING,
                List.of(token), Map.of(), Instant.now(), null
        );
        ExecutionContext context = new ExecutionContext(instance, definition);

        // Act
        List<ProcessEvent> events = executor.execute(token, serviceTask, context);

        // Assert
        assertThat(events).isEmpty();
    }

    @Test
    @DisplayName("Should support multiple handlers for different node types")
    void shouldSupportMultipleHandlers() {
        // Arrange
        NodeHandler startHandler = (token, node, context) -> List.of(
                new TokenMovedEvent(UUIDv7.generate(), context.getProcessInstance().getId(),
                        token.getId(), node.id(), "next", Instant.now(), 1L)
        );
        NodeHandler serviceHandler = (token, node, context) -> List.of();

        TokenExecutor executor = new TokenExecutor(Map.of(
                NodeType.START_EVENT, startHandler,
                NodeType.SERVICE_TASK, serviceHandler
        ));

        StartEvent startEvent = new StartEvent("start1", "Start", List.of(), List.of("flow1"));
        ServiceTask serviceTask = new ServiceTask("task1", "Task",
                List.of("flow1"), List.of("flow2"), "topic", 0, Duration.ZERO);
        EndEvent endEvent = new EndEvent("end1", "End", List.of("flow2"), List.of(), null);
        SequenceFlow flow1 = new SequenceFlow("flow1", "start1", "task1", null);
        SequenceFlow flow2 = new SequenceFlow("flow2", "task1", "end1", null);

        ProcessDefinition definition = ProcessDefinition.create("test", 1, "Test", "<xml/>",
                List.of(startEvent, serviceTask, endEvent), List.of(flow1, flow2));

        Token token = Token.create("start1");
        ProcessInstance instance = ProcessInstance.restore(
                UUID.randomUUID(), definition.getId(), null, null, ProcessState.RUNNING,
                List.of(token), Map.of(), Instant.now(), null
        );

        // Act & Assert
        List<ProcessEvent> startEvents = executor.execute(token, startEvent, new ExecutionContext(instance, definition));
        assertThat(startEvents).hasSize(1);

        List<ProcessEvent> serviceEvents = executor.execute(token, serviceTask, new ExecutionContext(instance, definition));
        assertThat(serviceEvents).isEmpty();
    }
}
