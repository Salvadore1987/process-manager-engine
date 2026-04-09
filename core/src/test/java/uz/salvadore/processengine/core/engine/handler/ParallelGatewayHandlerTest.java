package uz.salvadore.processengine.core.engine.handler;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import uz.salvadore.processengine.core.domain.enums.ProcessState;
import uz.salvadore.processengine.core.domain.enums.TokenState;
import uz.salvadore.processengine.core.domain.event.ProcessEvent;
import uz.salvadore.processengine.core.domain.event.TaskCompletedEvent;
import uz.salvadore.processengine.core.domain.event.TokenMovedEvent;
import uz.salvadore.processengine.core.domain.model.EndEvent;
import uz.salvadore.processengine.core.domain.model.ParallelGateway;
import uz.salvadore.processengine.core.domain.model.ProcessDefinition;
import uz.salvadore.processengine.core.domain.model.ProcessInstance;
import uz.salvadore.processengine.core.domain.model.SequenceFlow;
import uz.salvadore.processengine.core.domain.model.ServiceTask;
import uz.salvadore.processengine.core.domain.model.StartEvent;
import uz.salvadore.processengine.core.domain.model.Token;
import uz.salvadore.processengine.core.engine.context.ExecutionContext;
import uz.salvadore.processengine.core.adapter.inmemory.InMemorySequenceGenerator;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ParallelGatewayHandlerTest {

    private final InMemorySequenceGenerator sequenceGenerator = new InMemorySequenceGenerator();
    private final ParallelGatewayHandler handler = new ParallelGatewayHandler(sequenceGenerator);

    @Nested
    @DisplayName("Fork (1 incoming, N outgoing)")
    class ForkTests {

        @Test
        @DisplayName("Should complete source token and create N new tokens for each outgoing flow")
        void shouldForkIntoMultipleTokens() {
            // Arrange
            ParallelGateway gateway = new ParallelGateway("fork1", "Fork",
                    List.of("flow_in"), List.of("flow_out1", "flow_out2"));
            StartEvent startEvent = new StartEvent("start1", "Start", List.of(), List.of("flow_in"));
            ServiceTask task1 = new ServiceTask("task1", "Task1", List.of("flow_out1"), List.of("flow_end1"),
                    "topic1", 0, Duration.ZERO);
            ServiceTask task2 = new ServiceTask("task2", "Task2", List.of("flow_out2"), List.of("flow_end2"),
                    "topic2", 0, Duration.ZERO);
            EndEvent endEvent = new EndEvent("end1", "End", List.of("flow_end1", "flow_end2"), List.of(), null);

            SequenceFlow flowIn = new SequenceFlow("flow_in", "start1", "fork1", null);
            SequenceFlow flowOut1 = new SequenceFlow("flow_out1", "fork1", "task1", null);
            SequenceFlow flowOut2 = new SequenceFlow("flow_out2", "fork1", "task2", null);
            SequenceFlow flowEnd1 = new SequenceFlow("flow_end1", "task1", "end1", null);
            SequenceFlow flowEnd2 = new SequenceFlow("flow_end2", "task2", "end1", null);

            ProcessDefinition definition = ProcessDefinition.create("test", 1, "Test", "<xml/>",
                    List.of(startEvent, gateway, task1, task2, endEvent),
                    List.of(flowIn, flowOut1, flowOut2, flowEnd1, flowEnd2));

            Token token = Token.create("fork1");
            UUID processInstanceId = UUID.randomUUID();
            ProcessInstance instance = ProcessInstance.restore(
                    processInstanceId, definition.getId(), null, null, ProcessState.RUNNING,
                    List.of(token), Map.of(), Instant.now(), null
            );
            ExecutionContext context = new ExecutionContext(instance, definition);

            // Act
            List<ProcessEvent> events = handler.handle(token, gateway, context);

            // Assert
            // 1 TaskCompletedEvent (source token) + 2 TokenMovedEvent (new tokens)
            assertThat(events).hasSize(3);

            assertThat(events.get(0)).isInstanceOf(TaskCompletedEvent.class);
            TaskCompletedEvent completedEvent = (TaskCompletedEvent) events.get(0);
            assertThat(completedEvent.tokenId()).isEqualTo(token.getId());
            assertThat(completedEvent.nodeId()).isEqualTo("fork1");

            assertThat(events.get(1)).isInstanceOf(TokenMovedEvent.class);
            assertThat(events.get(2)).isInstanceOf(TokenMovedEvent.class);

            TokenMovedEvent moved1 = (TokenMovedEvent) events.get(1);
            TokenMovedEvent moved2 = (TokenMovedEvent) events.get(2);

            // New tokens should have different IDs from source and from each other
            assertThat(moved1.tokenId()).isNotEqualTo(token.getId());
            assertThat(moved2.tokenId()).isNotEqualTo(token.getId());
            assertThat(moved1.tokenId()).isNotEqualTo(moved2.tokenId());

            assertThat(moved1.toNodeId()).isEqualTo("task1");
            assertThat(moved2.toNodeId()).isEqualTo("task2");
        }

        @Test
        @DisplayName("Fork with 3 outgoing flows should create 3 new tokens")
        void shouldForkIntoThreeTokens() {
            // Arrange
            ParallelGateway gateway = new ParallelGateway("fork1", "Fork",
                    List.of("flow_in"), List.of("flow_out1", "flow_out2", "flow_out3"));
            StartEvent startEvent = new StartEvent("start1", "Start", List.of(), List.of("flow_in"));
            ServiceTask task1 = new ServiceTask("task1", "T1", List.of("flow_out1"), List.of("fe1"), "t1", 0, Duration.ZERO);
            ServiceTask task2 = new ServiceTask("task2", "T2", List.of("flow_out2"), List.of("fe2"), "t2", 0, Duration.ZERO);
            ServiceTask task3 = new ServiceTask("task3", "T3", List.of("flow_out3"), List.of("fe3"), "t3", 0, Duration.ZERO);
            EndEvent endEvent = new EndEvent("end1", "End", List.of("fe1", "fe2", "fe3"), List.of(), null);

            SequenceFlow flowIn = new SequenceFlow("flow_in", "start1", "fork1", null);
            SequenceFlow flowOut1 = new SequenceFlow("flow_out1", "fork1", "task1", null);
            SequenceFlow flowOut2 = new SequenceFlow("flow_out2", "fork1", "task2", null);
            SequenceFlow flowOut3 = new SequenceFlow("flow_out3", "fork1", "task3", null);
            SequenceFlow fe1 = new SequenceFlow("fe1", "task1", "end1", null);
            SequenceFlow fe2 = new SequenceFlow("fe2", "task2", "end1", null);
            SequenceFlow fe3 = new SequenceFlow("fe3", "task3", "end1", null);

            ProcessDefinition definition = ProcessDefinition.create("test", 1, "Test", "<xml/>",
                    List.of(startEvent, gateway, task1, task2, task3, endEvent),
                    List.of(flowIn, flowOut1, flowOut2, flowOut3, fe1, fe2, fe3));

            Token token = Token.create("fork1");
            ProcessInstance instance = ProcessInstance.restore(
                    UUID.randomUUID(), definition.getId(), null, null, ProcessState.RUNNING,
                    List.of(token), Map.of(), Instant.now(), null
            );
            ExecutionContext context = new ExecutionContext(instance, definition);

            // Act
            List<ProcessEvent> events = handler.handle(token, gateway, context);

            // Assert - 1 TaskCompleted + 3 TokenMoved
            assertThat(events).hasSize(4);
            long tokenMovedCount = events.stream().filter(e -> e instanceof TokenMovedEvent).count();
            assertThat(tokenMovedCount).isEqualTo(3);
        }
    }

    @Nested
    @DisplayName("Join (N incoming, 1 outgoing)")
    class JoinTests {

        @Test
        @DisplayName("Should return empty list when not all tokens have arrived")
        void shouldReturnEmptyWhenNotAllTokensArrived() {
            // Arrange
            ParallelGateway gateway = new ParallelGateway("join1", "Join",
                    List.of("flow_in1", "flow_in2"), List.of("flow_out"));
            StartEvent startEvent = new StartEvent("start1", "Start", List.of(), List.of("flow_in1"));
            EndEvent endEvent = new EndEvent("end1", "End", List.of("flow_out"), List.of(), null);

            SequenceFlow flowIn1 = new SequenceFlow("flow_in1", "start1", "join1", null);
            SequenceFlow flowIn2 = new SequenceFlow("flow_in2", "task2", "join1", null);
            SequenceFlow flowOut = new SequenceFlow("flow_out", "join1", "end1", null);

            ProcessDefinition definition = ProcessDefinition.create("test", 1, "Test", "<xml/>",
                    List.of(startEvent, gateway, endEvent),
                    List.of(flowIn1, flowIn2, flowOut));

            // Only 1 token at the gateway but 2 are needed
            Token token = Token.create("join1");
            ProcessInstance instance = ProcessInstance.restore(
                    UUID.randomUUID(), definition.getId(), null, null, ProcessState.RUNNING,
                    List.of(token), Map.of(), Instant.now(), null
            );
            ExecutionContext context = new ExecutionContext(instance, definition);

            // Act
            List<ProcessEvent> events = handler.handle(token, gateway, context);

            // Assert
            assertThat(events).isEmpty();
        }

        @Test
        @DisplayName("Should complete all waiting tokens and create one outgoing token when all tokens arrived")
        void shouldJoinWhenAllTokensArrived() {
            // Arrange
            ParallelGateway gateway = new ParallelGateway("join1", "Join",
                    List.of("flow_in1", "flow_in2"), List.of("flow_out"));
            StartEvent startEvent = new StartEvent("start1", "Start", List.of(), List.of("flow_in1"));
            EndEvent endEvent = new EndEvent("end1", "End", List.of("flow_out"), List.of(), null);

            SequenceFlow flowIn1 = new SequenceFlow("flow_in1", "task1", "join1", null);
            SequenceFlow flowIn2 = new SequenceFlow("flow_in2", "task2", "join1", null);
            SequenceFlow flowOut = new SequenceFlow("flow_out", "join1", "end1", null);

            ProcessDefinition definition = ProcessDefinition.create("test", 1, "Test", "<xml/>",
                    List.of(startEvent, gateway, endEvent),
                    List.of(flowIn1, flowIn2, flowOut));

            // 2 tokens at the gateway, 2 are needed
            Token token1 = Token.restore(UUID.randomUUID(), "join1", TokenState.ACTIVE);
            Token token2 = Token.restore(UUID.randomUUID(), "join1", TokenState.ACTIVE);
            UUID processInstanceId = UUID.randomUUID();
            ProcessInstance instance = ProcessInstance.restore(
                    processInstanceId, definition.getId(), null, null, ProcessState.RUNNING,
                    List.of(token1, token2), Map.of(), Instant.now(), null
            );
            ExecutionContext context = new ExecutionContext(instance, definition);

            // Act
            List<ProcessEvent> events = handler.handle(token1, gateway, context);

            // Assert
            // 2 TaskCompletedEvent (one per waiting token) + 1 TokenMovedEvent (outgoing)
            assertThat(events).hasSize(3);

            long completedCount = events.stream().filter(e -> e instanceof TaskCompletedEvent).count();
            assertThat(completedCount).isEqualTo(2);

            long movedCount = events.stream().filter(e -> e instanceof TokenMovedEvent).count();
            assertThat(movedCount).isEqualTo(1);

            TokenMovedEvent movedEvent = events.stream()
                    .filter(e -> e instanceof TokenMovedEvent)
                    .map(e -> (TokenMovedEvent) e)
                    .findFirst()
                    .orElseThrow();
            assertThat(movedEvent.toNodeId()).isEqualTo("end1");
        }

        @Test
        @DisplayName("Should not count completed tokens at the join gateway")
        void shouldNotCountCompletedTokensAtJoin() {
            // Arrange
            ParallelGateway gateway = new ParallelGateway("join1", "Join",
                    List.of("flow_in1", "flow_in2"), List.of("flow_out"));
            StartEvent startEvent = new StartEvent("start1", "Start", List.of(), List.of("flow_in1"));
            EndEvent endEvent = new EndEvent("end1", "End", List.of("flow_out"), List.of(), null);

            SequenceFlow flowIn1 = new SequenceFlow("flow_in1", "task1", "join1", null);
            SequenceFlow flowIn2 = new SequenceFlow("flow_in2", "task2", "join1", null);
            SequenceFlow flowOut = new SequenceFlow("flow_out", "join1", "end1", null);

            ProcessDefinition definition = ProcessDefinition.create("test", 1, "Test", "<xml/>",
                    List.of(startEvent, gateway, endEvent),
                    List.of(flowIn1, flowIn2, flowOut));

            // 1 active token + 1 completed token at join (completed should not count)
            Token activeToken = Token.restore(UUID.randomUUID(), "join1", TokenState.ACTIVE);
            Token completedToken = Token.restore(UUID.randomUUID(), "join1", TokenState.COMPLETED);
            ProcessInstance instance = ProcessInstance.restore(
                    UUID.randomUUID(), definition.getId(), null, null, ProcessState.RUNNING,
                    List.of(activeToken, completedToken), Map.of(), Instant.now(), null
            );
            ExecutionContext context = new ExecutionContext(instance, definition);

            // Act
            List<ProcessEvent> events = handler.handle(activeToken, gateway, context);

            // Assert - only 1 active token, need 2 => empty
            assertThat(events).isEmpty();
        }
    }

    @Test
    @DisplayName("Fork is detected by incomingFlows.size() <= 1")
    void shouldDetectForkBySingleIncomingFlow() {
        // Arrange - gateway with 0 incoming flows (edge case) should be treated as fork
        ParallelGateway gateway = new ParallelGateway("fork1", "Fork",
                List.of(), List.of("flow_out1", "flow_out2"));
        StartEvent startEvent = new StartEvent("start1", "Start", List.of(), List.of("f_in"));
        ServiceTask task1 = new ServiceTask("task1", "T1", List.of("flow_out1"), List.of("fe1"), "t1", 0, Duration.ZERO);
        ServiceTask task2 = new ServiceTask("task2", "T2", List.of("flow_out2"), List.of("fe2"), "t2", 0, Duration.ZERO);
        EndEvent endEvent = new EndEvent("end1", "End", List.of("fe1", "fe2"), List.of(), null);

        SequenceFlow fIn = new SequenceFlow("f_in", "start1", "fork1", null);
        SequenceFlow flowOut1 = new SequenceFlow("flow_out1", "fork1", "task1", null);
        SequenceFlow flowOut2 = new SequenceFlow("flow_out2", "fork1", "task2", null);
        SequenceFlow fe1 = new SequenceFlow("fe1", "task1", "end1", null);
        SequenceFlow fe2 = new SequenceFlow("fe2", "task2", "end1", null);

        ProcessDefinition definition = ProcessDefinition.create("test", 1, "Test", "<xml/>",
                List.of(startEvent, gateway, task1, task2, endEvent),
                List.of(fIn, flowOut1, flowOut2, fe1, fe2));

        Token token = Token.create("fork1");
        ProcessInstance instance = ProcessInstance.restore(
                UUID.randomUUID(), definition.getId(), null, null, ProcessState.RUNNING,
                List.of(token), Map.of(), Instant.now(), null
        );
        ExecutionContext context = new ExecutionContext(instance, definition);

        // Act
        List<ProcessEvent> events = handler.handle(token, gateway, context);

        // Assert - treated as fork: 1 TaskCompleted + 2 TokenMoved
        assertThat(events).hasSize(3);
        assertThat(events.get(0)).isInstanceOf(TaskCompletedEvent.class);
    }
}
