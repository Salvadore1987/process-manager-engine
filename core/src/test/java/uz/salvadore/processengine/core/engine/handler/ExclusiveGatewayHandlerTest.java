package uz.salvadore.processengine.core.engine.handler;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import uz.salvadore.processengine.core.domain.enums.ProcessState;
import uz.salvadore.processengine.core.domain.event.ProcessEvent;
import uz.salvadore.processengine.core.domain.event.TokenMovedEvent;
import uz.salvadore.processengine.core.domain.model.ConditionExpression;
import uz.salvadore.processengine.core.domain.model.EndEvent;
import uz.salvadore.processengine.core.domain.model.ExclusiveGateway;
import uz.salvadore.processengine.core.domain.model.ProcessDefinition;
import uz.salvadore.processengine.core.domain.model.ProcessInstance;
import uz.salvadore.processengine.core.domain.model.SequenceFlow;
import uz.salvadore.processengine.core.domain.model.ServiceTask;
import uz.salvadore.processengine.core.domain.model.StartEvent;
import uz.salvadore.processengine.core.domain.model.Token;
import uz.salvadore.processengine.core.engine.condition.SimpleConditionEvaluator;
import uz.salvadore.processengine.core.engine.context.ExecutionContext;
import uz.salvadore.processengine.core.adapter.inmemory.InMemorySequenceGenerator;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExclusiveGatewayHandlerTest {

    private final SimpleConditionEvaluator conditionEvaluator = new SimpleConditionEvaluator();
    private final InMemorySequenceGenerator sequenceGenerator = new InMemorySequenceGenerator();
    private final ExclusiveGatewayHandler handler = new ExclusiveGatewayHandler(conditionEvaluator, sequenceGenerator);

    @Nested
    @DisplayName("Condition-based routing")
    class ConditionRoutingTests {

        @Test
        @DisplayName("Should route to first matching condition flow")
        void shouldRouteToFirstMatchingConditionFlow() {
            // Arrange
            ExclusiveGateway gateway = new ExclusiveGateway("gw1", "Amount Check",
                    List.of("flow_in"), List.of("flow_high", "flow_standard"), null);
            StartEvent startEvent = new StartEvent("start1", "Start", List.of(), List.of("flow_in"));
            ServiceTask highTask = new ServiceTask("fraud", "Fraud", List.of("flow_high"), List.of("flow_end1"),
                    "fraud.check", 0, Duration.ZERO);
            ServiceTask standardTask = new ServiceTask("standard", "Standard", List.of("flow_standard"), List.of("flow_end2"),
                    "standard.check", 0, Duration.ZERO);
            EndEvent endEvent = new EndEvent("end1", "End", List.of("flow_end1", "flow_end2"), List.of(), null);

            SequenceFlow flowIn = new SequenceFlow("flow_in", "start1", "gw1", null);
            SequenceFlow flowHigh = new SequenceFlow("flow_high", "gw1", "fraud",
                    new ConditionExpression("${amount > 10000}"));
            SequenceFlow flowStandard = new SequenceFlow("flow_standard", "gw1", "standard",
                    new ConditionExpression("${amount <= 10000}"));
            SequenceFlow flowEnd1 = new SequenceFlow("flow_end1", "fraud", "end1", null);
            SequenceFlow flowEnd2 = new SequenceFlow("flow_end2", "standard", "end1", null);

            ProcessDefinition definition = ProcessDefinition.create("test", 1, "Test", "<xml/>",
                    List.of(startEvent, gateway, highTask, standardTask, endEvent),
                    List.of(flowIn, flowHigh, flowStandard, flowEnd1, flowEnd2));

            Token token = Token.create("gw1");
            Map<String, Object> variables = Map.of("amount", 15000L);
            ProcessInstance instance = ProcessInstance.restore(
                    UUID.randomUUID(), definition.getId(), null, ProcessState.RUNNING,
                    List.of(token), variables, Instant.now(), null
            );
            ExecutionContext context = new ExecutionContext(instance, definition);

            // Act
            List<ProcessEvent> events = handler.handle(token, gateway, context);

            // Assert
            assertThat(events).hasSize(1);
            TokenMovedEvent movedEvent = (TokenMovedEvent) events.getFirst();
            assertThat(movedEvent.toNodeId()).isEqualTo("fraud");
        }

        @Test
        @DisplayName("Should route to second condition when first does not match")
        void shouldRouteToSecondConditionWhenFirstDoesNotMatch() {
            // Arrange
            ExclusiveGateway gateway = new ExclusiveGateway("gw1", "Amount Check",
                    List.of("flow_in"), List.of("flow_high", "flow_standard"), null);
            StartEvent startEvent = new StartEvent("start1", "Start", List.of(), List.of("flow_in"));
            ServiceTask highTask = new ServiceTask("fraud", "Fraud", List.of("flow_high"), List.of("flow_end1"),
                    "fraud.check", 0, Duration.ZERO);
            ServiceTask standardTask = new ServiceTask("standard", "Standard", List.of("flow_standard"), List.of("flow_end2"),
                    "standard.check", 0, Duration.ZERO);
            EndEvent endEvent = new EndEvent("end1", "End", List.of("flow_end1", "flow_end2"), List.of(), null);

            SequenceFlow flowIn = new SequenceFlow("flow_in", "start1", "gw1", null);
            SequenceFlow flowHigh = new SequenceFlow("flow_high", "gw1", "fraud",
                    new ConditionExpression("${amount > 10000}"));
            SequenceFlow flowStandard = new SequenceFlow("flow_standard", "gw1", "standard",
                    new ConditionExpression("${amount <= 10000}"));
            SequenceFlow flowEnd1 = new SequenceFlow("flow_end1", "fraud", "end1", null);
            SequenceFlow flowEnd2 = new SequenceFlow("flow_end2", "standard", "end1", null);

            ProcessDefinition definition = ProcessDefinition.create("test", 1, "Test", "<xml/>",
                    List.of(startEvent, gateway, highTask, standardTask, endEvent),
                    List.of(flowIn, flowHigh, flowStandard, flowEnd1, flowEnd2));

            Token token = Token.create("gw1");
            Map<String, Object> variables = Map.of("amount", 5000L);
            ProcessInstance instance = ProcessInstance.restore(
                    UUID.randomUUID(), definition.getId(), null, ProcessState.RUNNING,
                    List.of(token), variables, Instant.now(), null
            );
            ExecutionContext context = new ExecutionContext(instance, definition);

            // Act
            List<ProcessEvent> events = handler.handle(token, gateway, context);

            // Assert
            assertThat(events).hasSize(1);
            TokenMovedEvent movedEvent = (TokenMovedEvent) events.getFirst();
            assertThat(movedEvent.toNodeId()).isEqualTo("standard");
        }
    }

    @Nested
    @DisplayName("Default flow fallback")
    class DefaultFlowTests {

        @Test
        @DisplayName("Should fall back to default flow when no condition matches")
        void shouldFallBackToDefaultFlow() {
            // Arrange
            ExclusiveGateway gateway = new ExclusiveGateway("gw1", "Gateway",
                    List.of("flow_in"), List.of("flow_cond", "flow_default"), null);
            StartEvent startEvent = new StartEvent("start1", "Start", List.of(), List.of("flow_in"));
            ServiceTask condTask = new ServiceTask("condTask", "Cond", List.of("flow_cond"), List.of("flow_end1"),
                    "cond.topic", 0, Duration.ZERO);
            ServiceTask defaultTask = new ServiceTask("defaultTask", "Default", List.of("flow_default"), List.of("flow_end2"),
                    "default.topic", 0, Duration.ZERO);
            EndEvent endEvent = new EndEvent("end1", "End", List.of("flow_end1", "flow_end2"), List.of(), null);

            SequenceFlow flowIn = new SequenceFlow("flow_in", "start1", "gw1", null);
            SequenceFlow flowCond = new SequenceFlow("flow_cond", "gw1", "condTask",
                    new ConditionExpression("${amount > 99999}"));
            SequenceFlow flowDefault = new SequenceFlow("flow_default", "gw1", "defaultTask", null);
            SequenceFlow flowEnd1 = new SequenceFlow("flow_end1", "condTask", "end1", null);
            SequenceFlow flowEnd2 = new SequenceFlow("flow_end2", "defaultTask", "end1", null);

            ProcessDefinition definition = ProcessDefinition.create("test", 1, "Test", "<xml/>",
                    List.of(startEvent, gateway, condTask, defaultTask, endEvent),
                    List.of(flowIn, flowCond, flowDefault, flowEnd1, flowEnd2));

            Token token = Token.create("gw1");
            Map<String, Object> variables = Map.of("amount", 100L);
            ProcessInstance instance = ProcessInstance.restore(
                    UUID.randomUUID(), definition.getId(), null, ProcessState.RUNNING,
                    List.of(token), variables, Instant.now(), null
            );
            ExecutionContext context = new ExecutionContext(instance, definition);

            // Act
            List<ProcessEvent> events = handler.handle(token, gateway, context);

            // Assert
            assertThat(events).hasSize(1);
            TokenMovedEvent movedEvent = (TokenMovedEvent) events.getFirst();
            assertThat(movedEvent.toNodeId()).isEqualTo("defaultTask");
        }
    }

    @Nested
    @DisplayName("Error cases")
    class ErrorTests {

        @Test
        @DisplayName("Should throw when no outgoing flows exist")
        void shouldThrowWhenNoOutgoingFlows() {
            // Arrange
            ExclusiveGateway gateway = new ExclusiveGateway("gw1", "Gateway",
                    List.of("flow_in"), List.of(), null);
            StartEvent startEvent = new StartEvent("start1", "Start", List.of(), List.of("flow_in"));
            EndEvent endEvent = new EndEvent("end1", "End", List.of(), List.of(), null);
            SequenceFlow flowIn = new SequenceFlow("flow_in", "start1", "gw1", null);

            ProcessDefinition definition = ProcessDefinition.create("test", 1, "Test", "<xml/>",
                    List.of(startEvent, gateway, endEvent), List.of(flowIn));

            Token token = Token.create("gw1");
            ProcessInstance instance = ProcessInstance.restore(
                    UUID.randomUUID(), definition.getId(), null, ProcessState.RUNNING,
                    List.of(token), Map.of("amount", 100L), Instant.now(), null
            );
            ExecutionContext context = new ExecutionContext(instance, definition);

            // Act & Assert
            assertThatThrownBy(() -> handler.handle(token, gateway, context))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("no outgoing flows");
        }

        @Test
        @DisplayName("Should throw when no condition matches and no default flow")
        void shouldThrowWhenNoConditionMatchesAndNoDefault() {
            // Arrange
            ExclusiveGateway gateway = new ExclusiveGateway("gw1", "Gateway",
                    List.of("flow_in"), List.of("flow_cond"), null);
            StartEvent startEvent = new StartEvent("start1", "Start", List.of(), List.of("flow_in"));
            ServiceTask task = new ServiceTask("task1", "Task", List.of("flow_cond"), List.of("flow_end"),
                    "topic", 0, Duration.ZERO);
            EndEvent endEvent = new EndEvent("end1", "End", List.of("flow_end"), List.of(), null);

            SequenceFlow flowIn = new SequenceFlow("flow_in", "start1", "gw1", null);
            SequenceFlow flowCond = new SequenceFlow("flow_cond", "gw1", "task1",
                    new ConditionExpression("${amount > 99999}"));
            SequenceFlow flowEnd = new SequenceFlow("flow_end", "task1", "end1", null);

            ProcessDefinition definition = ProcessDefinition.create("test", 1, "Test", "<xml/>",
                    List.of(startEvent, gateway, task, endEvent), List.of(flowIn, flowCond, flowEnd));

            Token token = Token.create("gw1");
            Map<String, Object> variables = Map.of("amount", 100L);
            ProcessInstance instance = ProcessInstance.restore(
                    UUID.randomUUID(), definition.getId(), null, ProcessState.RUNNING,
                    List.of(token), variables, Instant.now(), null
            );
            ExecutionContext context = new ExecutionContext(instance, definition);

            // Act & Assert
            assertThatThrownBy(() -> handler.handle(token, gateway, context))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("No matching condition");
        }
    }

    @Nested
    @DisplayName("Explicit default flow (defaultFlowId attribute)")
    class ExplicitDefaultFlowTests {

        @Test
        @DisplayName("Should route to explicit defaultFlowId when no condition matches")
        void shouldRouteToExplicitDefaultFlow() {
            // Arrange
            ExclusiveGateway gateway = new ExclusiveGateway("gw1", "Gateway",
                    List.of("flow_in"), List.of("flow_cond", "flow_default"), "flow_default");
            StartEvent startEvent = new StartEvent("start1", "Start", List.of(), List.of("flow_in"));
            ServiceTask condTask = new ServiceTask("condTask", "Cond", List.of("flow_cond"), List.of("flow_end1"),
                    "cond.topic", 0, Duration.ZERO);
            ServiceTask defaultTask = new ServiceTask("defaultTask", "Default", List.of("flow_default"), List.of("flow_end2"),
                    "default.topic", 0, Duration.ZERO);
            EndEvent endEvent = new EndEvent("end1", "End", List.of("flow_end1", "flow_end2"), List.of(), null);

            SequenceFlow flowIn = new SequenceFlow("flow_in", "start1", "gw1", null);
            SequenceFlow flowCond = new SequenceFlow("flow_cond", "gw1", "condTask",
                    new ConditionExpression("${amount > 99999}"));
            SequenceFlow flowDefault = new SequenceFlow("flow_default", "gw1", "defaultTask",
                    new ConditionExpression("${amount < 0}")); // has condition but is default — should be ignored
            SequenceFlow flowEnd1 = new SequenceFlow("flow_end1", "condTask", "end1", null);
            SequenceFlow flowEnd2 = new SequenceFlow("flow_end2", "defaultTask", "end1", null);

            ProcessDefinition definition = ProcessDefinition.create("test", 1, "Test", "<xml/>",
                    List.of(startEvent, gateway, condTask, defaultTask, endEvent),
                    List.of(flowIn, flowCond, flowDefault, flowEnd1, flowEnd2));

            Token token = Token.create("gw1");
            Map<String, Object> variables = Map.of("amount", 500L);
            ProcessInstance instance = ProcessInstance.restore(
                    UUID.randomUUID(), definition.getId(), null, ProcessState.RUNNING,
                    List.of(token), variables, Instant.now(), null
            );
            ExecutionContext context = new ExecutionContext(instance, definition);

            // Act
            List<ProcessEvent> events = handler.handle(token, gateway, context);

            // Assert
            assertThat(events).hasSize(1);
            TokenMovedEvent movedEvent = (TokenMovedEvent) events.getFirst();
            assertThat(movedEvent.toNodeId()).isEqualTo("defaultTask");
        }

        @Test
        @DisplayName("Should ignore condition on explicit default flow and still route to it")
        void shouldIgnoreConditionOnDefaultFlow() {
            // Arrange — default flow has a condition expression, but since it's the explicit default,
            // its condition should not be evaluated and it should act as fallback
            ExclusiveGateway gateway = new ExclusiveGateway("gw1", "Gateway",
                    List.of("flow_in"), List.of("flow_cond", "flow_default"), "flow_default");
            StartEvent startEvent = new StartEvent("start1", "Start", List.of(), List.of("flow_in"));
            ServiceTask condTask = new ServiceTask("condTask", "Cond", List.of("flow_cond"), List.of("flow_end1"),
                    "cond.topic", 0, Duration.ZERO);
            ServiceTask defaultTask = new ServiceTask("defaultTask", "Default", List.of("flow_default"), List.of("flow_end2"),
                    "default.topic", 0, Duration.ZERO);
            EndEvent endEvent = new EndEvent("end1", "End", List.of("flow_end1", "flow_end2"), List.of(), null);

            SequenceFlow flowIn = new SequenceFlow("flow_in", "start1", "gw1", null);
            SequenceFlow flowCond = new SequenceFlow("flow_cond", "gw1", "condTask",
                    new ConditionExpression("${amount > 99999}"));
            // Default flow has condition that would also not match — but it should be used as fallback anyway
            SequenceFlow flowDefault = new SequenceFlow("flow_default", "gw1", "defaultTask",
                    new ConditionExpression("${amount == 42}"));
            SequenceFlow flowEnd1 = new SequenceFlow("flow_end1", "condTask", "end1", null);
            SequenceFlow flowEnd2 = new SequenceFlow("flow_end2", "defaultTask", "end1", null);

            ProcessDefinition definition = ProcessDefinition.create("test", 1, "Test", "<xml/>",
                    List.of(startEvent, gateway, condTask, defaultTask, endEvent),
                    List.of(flowIn, flowCond, flowDefault, flowEnd1, flowEnd2));

            Token token = Token.create("gw1");
            Map<String, Object> variables = Map.of("amount", 100L);
            ProcessInstance instance = ProcessInstance.restore(
                    UUID.randomUUID(), definition.getId(), null, ProcessState.RUNNING,
                    List.of(token), variables, Instant.now(), null
            );
            ExecutionContext context = new ExecutionContext(instance, definition);

            // Act
            List<ProcessEvent> events = handler.handle(token, gateway, context);

            // Assert
            assertThat(events).hasSize(1);
            TokenMovedEvent movedEvent = (TokenMovedEvent) events.getFirst();
            assertThat(movedEvent.toNodeId()).isEqualTo("defaultTask");
        }

        @Test
        @DisplayName("Should prefer condition match over explicit default flow")
        void shouldPreferConditionMatchOverDefault() {
            // Arrange
            ExclusiveGateway gateway = new ExclusiveGateway("gw1", "Gateway",
                    List.of("flow_in"), List.of("flow_cond", "flow_default"), "flow_default");
            StartEvent startEvent = new StartEvent("start1", "Start", List.of(), List.of("flow_in"));
            ServiceTask condTask = new ServiceTask("condTask", "Cond", List.of("flow_cond"), List.of("flow_end1"),
                    "cond.topic", 0, Duration.ZERO);
            ServiceTask defaultTask = new ServiceTask("defaultTask", "Default", List.of("flow_default"), List.of("flow_end2"),
                    "default.topic", 0, Duration.ZERO);
            EndEvent endEvent = new EndEvent("end1", "End", List.of("flow_end1", "flow_end2"), List.of(), null);

            SequenceFlow flowIn = new SequenceFlow("flow_in", "start1", "gw1", null);
            SequenceFlow flowCond = new SequenceFlow("flow_cond", "gw1", "condTask",
                    new ConditionExpression("${amount > 100}"));
            SequenceFlow flowDefault = new SequenceFlow("flow_default", "gw1", "defaultTask", null);
            SequenceFlow flowEnd1 = new SequenceFlow("flow_end1", "condTask", "end1", null);
            SequenceFlow flowEnd2 = new SequenceFlow("flow_end2", "defaultTask", "end1", null);

            ProcessDefinition definition = ProcessDefinition.create("test", 1, "Test", "<xml/>",
                    List.of(startEvent, gateway, condTask, defaultTask, endEvent),
                    List.of(flowIn, flowCond, flowDefault, flowEnd1, flowEnd2));

            Token token = Token.create("gw1");
            Map<String, Object> variables = Map.of("amount", 500L);
            ProcessInstance instance = ProcessInstance.restore(
                    UUID.randomUUID(), definition.getId(), null, ProcessState.RUNNING,
                    List.of(token), variables, Instant.now(), null
            );
            ExecutionContext context = new ExecutionContext(instance, definition);

            // Act
            List<ProcessEvent> events = handler.handle(token, gateway, context);

            // Assert
            assertThat(events).hasSize(1);
            TokenMovedEvent movedEvent = (TokenMovedEvent) events.getFirst();
            assertThat(movedEvent.toNodeId()).isEqualTo("condTask");
        }
    }

    @Test
    @DisplayName("Should set correct processInstanceId and tokenId in emitted event")
    void shouldSetCorrectIdsInEmittedEvent() {
        // Arrange
        ExclusiveGateway gateway = new ExclusiveGateway("gw1", "Gateway",
                List.of("flow_in"), List.of("flow_out"), null);
        StartEvent startEvent = new StartEvent("start1", "Start", List.of(), List.of("flow_in"));
        EndEvent endEvent = new EndEvent("end1", "End", List.of("flow_out"), List.of(), null);

        SequenceFlow flowIn = new SequenceFlow("flow_in", "start1", "gw1", null);
        SequenceFlow flowOut = new SequenceFlow("flow_out", "gw1", "end1", null);

        ProcessDefinition definition = ProcessDefinition.create("test", 1, "Test", "<xml/>",
                List.of(startEvent, gateway, endEvent), List.of(flowIn, flowOut));

        Token token = Token.create("gw1");
        UUID processInstanceId = UUID.randomUUID();
        ProcessInstance instance = ProcessInstance.restore(
                processInstanceId, definition.getId(), null, ProcessState.RUNNING,
                List.of(token), Map.of("amount", 100L), Instant.now(), null
        );
        ExecutionContext context = new ExecutionContext(instance, definition);

        // Act
        List<ProcessEvent> events = handler.handle(token, gateway, context);

        // Assert
        TokenMovedEvent movedEvent = (TokenMovedEvent) events.getFirst();
        assertThat(movedEvent.processInstanceId()).isEqualTo(processInstanceId);
        assertThat(movedEvent.tokenId()).isEqualTo(token.getId());
        assertThat(movedEvent.fromNodeId()).isEqualTo("gw1");
    }
}
