package uz.salvadore.processengine.core.engine.handler;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import uz.salvadore.processengine.core.domain.enums.ProcessState;
import uz.salvadore.processengine.core.domain.enums.TokenState;
import uz.salvadore.processengine.core.domain.event.ProcessCompletedEvent;
import uz.salvadore.processengine.core.domain.event.ProcessErrorEvent;
import uz.salvadore.processengine.core.domain.event.ProcessEvent;
import uz.salvadore.processengine.core.domain.event.TaskCompletedEvent;
import uz.salvadore.processengine.core.domain.model.EndEvent;
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

class EndEventHandlerTest {

    private final InMemorySequenceGenerator sequenceGenerator = new InMemorySequenceGenerator();
    private final EndEventHandler handler = new EndEventHandler(sequenceGenerator);

    private ProcessDefinition createSimpleDefinition() {
        StartEvent startEvent = new StartEvent("start1", "Start", List.of(), List.of("flow1"));
        EndEvent endEvent = new EndEvent("end1", "End", List.of("flow1"), List.of(), null);
        SequenceFlow flow = new SequenceFlow("flow1", "start1", "end1", null);
        return ProcessDefinition.create("test", 1, "Test", "<xml/>",
                List.of(startEvent, endEvent), List.of(flow));
    }

    @Nested
    @DisplayName("Normal end event (no error code)")
    class NormalEndEventTests {

        @Test
        @DisplayName("Should emit TaskCompletedEvent for current token")
        void shouldEmitTaskCompletedEvent() {
            // Arrange
            ProcessDefinition definition = createSimpleDefinition();
            EndEvent endEvent = new EndEvent("end1", "End", List.of("flow1"), List.of(), null);
            Token token = Token.create("end1");
            ProcessInstance instance = ProcessInstance.restore(
                    UUID.randomUUID(), definition.getId(), null, ProcessState.RUNNING,
                    List.of(token), Map.of(), Instant.now(), null
            );
            ExecutionContext context = new ExecutionContext(instance, definition);

            // Act
            List<ProcessEvent> events = handler.handle(token, endEvent, context);

            // Assert
            assertThat(events).hasSizeGreaterThanOrEqualTo(1);
            assertThat(events.getFirst()).isInstanceOf(TaskCompletedEvent.class);

            TaskCompletedEvent completedEvent = (TaskCompletedEvent) events.getFirst();
            assertThat(completedEvent.tokenId()).isEqualTo(token.getId());
            assertThat(completedEvent.nodeId()).isEqualTo("end1");
        }

        @Test
        @DisplayName("Should emit ProcessCompletedEvent when all other tokens are completed")
        void shouldEmitProcessCompletedEventWhenAllTokensCompleted() {
            // Arrange
            ProcessDefinition definition = createSimpleDefinition();
            EndEvent endEvent = new EndEvent("end1", "End", List.of("flow1"), List.of(), null);
            Token activeToken = Token.create("end1");
            Token completedToken = Token.restore(UUID.randomUUID(), "task1", TokenState.COMPLETED);
            ProcessInstance instance = ProcessInstance.restore(
                    UUID.randomUUID(), definition.getId(), null, ProcessState.RUNNING,
                    List.of(activeToken, completedToken), Map.of(), Instant.now(), null
            );
            ExecutionContext context = new ExecutionContext(instance, definition);

            // Act
            List<ProcessEvent> events = handler.handle(activeToken, endEvent, context);

            // Assert
            assertThat(events).hasSize(2);
            assertThat(events.get(0)).isInstanceOf(TaskCompletedEvent.class);
            assertThat(events.get(1)).isInstanceOf(ProcessCompletedEvent.class);
        }

        @Test
        @DisplayName("Should NOT emit ProcessCompletedEvent when other tokens are still active")
        void shouldNotEmitProcessCompletedEventWhenOtherTokensActive() {
            // Arrange
            ProcessDefinition definition = createSimpleDefinition();
            EndEvent endEvent = new EndEvent("end1", "End", List.of("flow1"), List.of(), null);
            Token activeToken = Token.create("end1");
            Token otherActiveToken = Token.create("task1");
            ProcessInstance instance = ProcessInstance.restore(
                    UUID.randomUUID(), definition.getId(), null, ProcessState.RUNNING,
                    List.of(activeToken, otherActiveToken), Map.of(), Instant.now(), null
            );
            ExecutionContext context = new ExecutionContext(instance, definition);

            // Act
            List<ProcessEvent> events = handler.handle(activeToken, endEvent, context);

            // Assert
            assertThat(events).hasSize(1);
            assertThat(events.getFirst()).isInstanceOf(TaskCompletedEvent.class);
        }

        @Test
        @DisplayName("Should NOT emit ProcessCompletedEvent when other tokens are waiting")
        void shouldNotEmitProcessCompletedEventWhenOtherTokensWaiting() {
            // Arrange
            ProcessDefinition definition = createSimpleDefinition();
            EndEvent endEvent = new EndEvent("end1", "End", List.of("flow1"), List.of(), null);
            Token activeToken = Token.create("end1");
            Token waitingToken = Token.restore(UUID.randomUUID(), "task2", TokenState.WAITING);
            ProcessInstance instance = ProcessInstance.restore(
                    UUID.randomUUID(), definition.getId(), null, ProcessState.RUNNING,
                    List.of(activeToken, waitingToken), Map.of(), Instant.now(), null
            );
            ExecutionContext context = new ExecutionContext(instance, definition);

            // Act
            List<ProcessEvent> events = handler.handle(activeToken, endEvent, context);

            // Assert
            assertThat(events).hasSize(1);
            assertThat(events.getFirst()).isInstanceOf(TaskCompletedEvent.class);
        }

        @Test
        @DisplayName("Should emit ProcessCompletedEvent when this is the only token")
        void shouldEmitProcessCompletedEventWhenSingleToken() {
            // Arrange
            ProcessDefinition definition = createSimpleDefinition();
            EndEvent endEvent = new EndEvent("end1", "End", List.of("flow1"), List.of(), null);
            Token token = Token.create("end1");
            ProcessInstance instance = ProcessInstance.restore(
                    UUID.randomUUID(), definition.getId(), null, ProcessState.RUNNING,
                    List.of(token), Map.of(), Instant.now(), null
            );
            ExecutionContext context = new ExecutionContext(instance, definition);

            // Act
            List<ProcessEvent> events = handler.handle(token, endEvent, context);

            // Assert
            assertThat(events).hasSize(2);
            assertThat(events.get(1)).isInstanceOf(ProcessCompletedEvent.class);
        }
    }

    @Nested
    @DisplayName("Error end event (with error code)")
    class ErrorEndEventTests {

        @Test
        @DisplayName("Should emit ProcessErrorEvent when EndEvent has errorCode")
        void shouldEmitProcessErrorEventWhenEndEventHasErrorCode() {
            // Arrange
            StartEvent startEvent = new StartEvent("start1", "Start", List.of(), List.of("flow1"));
            EndEvent endEvent = new EndEvent("end1", "End", List.of("flow1"), List.of(), "PAYMENT_FAILED");
            SequenceFlow flow = new SequenceFlow("flow1", "start1", "end1", null);
            ProcessDefinition definition = ProcessDefinition.create("test", 1, "Test", "<xml/>",
                    List.of(startEvent, endEvent), List.of(flow));

            Token token = Token.create("end1");
            ProcessInstance instance = ProcessInstance.restore(
                    UUID.randomUUID(), definition.getId(), null, ProcessState.RUNNING,
                    List.of(token), Map.of(), Instant.now(), null
            );
            ExecutionContext context = new ExecutionContext(instance, definition);

            // Act
            List<ProcessEvent> events = handler.handle(token, endEvent, context);

            // Assert
            assertThat(events).hasSize(2);
            assertThat(events.get(0)).isInstanceOf(TaskCompletedEvent.class);
            assertThat(events.get(1)).isInstanceOf(ProcessErrorEvent.class);

            ProcessErrorEvent errorEvent = (ProcessErrorEvent) events.get(1);
            assertThat(errorEvent.errorCode()).isEqualTo("PAYMENT_FAILED");
            assertThat(errorEvent.errorMessage()).contains("PAYMENT_FAILED");
        }

        @Test
        @DisplayName("Should emit ProcessErrorEvent instead of ProcessCompletedEvent even when all tokens completed")
        void shouldPreferErrorOverCompleted() {
            // Arrange
            StartEvent startEvent = new StartEvent("start1", "Start", List.of(), List.of("flow1"));
            EndEvent endEvent = new EndEvent("end1", "End", List.of("flow1"), List.of(), "SOME_ERROR");
            SequenceFlow flow = new SequenceFlow("flow1", "start1", "end1", null);
            ProcessDefinition definition = ProcessDefinition.create("test", 1, "Test", "<xml/>",
                    List.of(startEvent, endEvent), List.of(flow));

            Token token = Token.create("end1");
            // Single token scenario - normally would also emit ProcessCompletedEvent
            ProcessInstance instance = ProcessInstance.restore(
                    UUID.randomUUID(), definition.getId(), null, ProcessState.RUNNING,
                    List.of(token), Map.of(), Instant.now(), null
            );
            ExecutionContext context = new ExecutionContext(instance, definition);

            // Act
            List<ProcessEvent> events = handler.handle(token, endEvent, context);

            // Assert - error path: TaskCompleted + ProcessError (no ProcessCompleted)
            assertThat(events).hasSize(2);
            assertThat(events.get(1)).isInstanceOf(ProcessErrorEvent.class);
            assertThat(events).noneMatch(e -> e instanceof ProcessCompletedEvent);
        }
    }
}
