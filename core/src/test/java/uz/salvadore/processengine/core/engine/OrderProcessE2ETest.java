package uz.salvadore.processengine.core.engine;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import uz.salvadore.processengine.core.adapter.inmemory.InMemoryEventStore;
import uz.salvadore.processengine.core.domain.enums.NodeType;
import uz.salvadore.processengine.core.domain.enums.ProcessState;
import uz.salvadore.processengine.core.domain.enums.TokenState;
import uz.salvadore.processengine.core.domain.event.ProcessEvent;
import uz.salvadore.processengine.core.domain.model.ProcessDefinition;
import uz.salvadore.processengine.core.domain.model.ProcessInstance;
import uz.salvadore.processengine.core.domain.model.Token;
import uz.salvadore.processengine.core.engine.condition.SimpleConditionEvaluator;
import uz.salvadore.processengine.core.engine.eventsourcing.EventApplier;
import uz.salvadore.processengine.core.engine.eventsourcing.EventSequencer;
import uz.salvadore.processengine.core.engine.eventsourcing.ProcessInstanceProjection;
import uz.salvadore.processengine.core.engine.handler.CallActivityHandler;
import uz.salvadore.processengine.core.engine.handler.EndEventHandler;
import uz.salvadore.processengine.core.engine.handler.ExclusiveGatewayHandler;
import uz.salvadore.processengine.core.engine.handler.NodeHandler;
import uz.salvadore.processengine.core.engine.handler.ParallelGatewayHandler;
import uz.salvadore.processengine.core.engine.handler.ServiceTaskHandler;
import uz.salvadore.processengine.core.engine.handler.StartEventHandler;
import uz.salvadore.processengine.core.parser.BpmnParser;
import uz.salvadore.processengine.core.port.outgoing.MessageTransport;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end test using the parsed example-order-process.bpmn.
 * Runs the full order processing workflow through the engine.
 */
class OrderProcessE2ETest {

    static class RecordingMessageTransport implements MessageTransport {
        final List<SendCall> sendCalls = new ArrayList<>();

        record SendCall(String topic, UUID correlationId, Map<String, Object> payload) {}

        @Override
        public void send(String topic, UUID correlationId, Map<String, Object> payload) {
            sendCalls.add(new SendCall(topic, correlationId, payload));
        }

        @Override
        public void subscribe(String topic, Consumer<MessageResult> callback) {
            // no-op
        }
    }

    private InMemoryEventStore eventStore;
    private ProcessEngine engine;
    private RecordingMessageTransport messageTransport;
    private ProcessDefinition orderProcess;

    @BeforeEach
    void setUp() throws IOException {
        eventStore = new InMemoryEventStore();
        ProcessDefinitionRepository definitionRepository = new ProcessDefinitionRepository();
        EventSequencer eventSequencer = new EventSequencer();
        messageTransport = new RecordingMessageTransport();

        SimpleConditionEvaluator conditionEvaluator = new SimpleConditionEvaluator();

        Map<NodeType, NodeHandler> handlers = Map.of(
                NodeType.START_EVENT, new StartEventHandler(eventSequencer),
                NodeType.END_EVENT, new EndEventHandler(eventSequencer),
                NodeType.SERVICE_TASK, new ServiceTaskHandler(messageTransport),
                NodeType.EXCLUSIVE_GATEWAY, new ExclusiveGatewayHandler(conditionEvaluator, eventSequencer),
                NodeType.PARALLEL_GATEWAY, new ParallelGatewayHandler(eventSequencer),
                NodeType.CALL_ACTIVITY, new CallActivityHandler(eventSequencer)
        );

        TokenExecutor tokenExecutor = new TokenExecutor(handlers);
        engine = new ProcessEngine(eventStore, definitionRepository, tokenExecutor, eventSequencer);

        // Parse and deploy the order process BPMN
        BpmnParser parser = new BpmnParser();
        InputStream bpmnStream = getClass().getResourceAsStream("/bpmn/example-order-process.bpmn");
        assertThat(bpmnStream).isNotNull();
        String bpmnXml = new String(bpmnStream.readAllBytes());
        List<ProcessDefinition> definitions = parser.parse(bpmnXml);
        orderProcess = definitions.getFirst();
        engine.deploy(orderProcess);
    }

    @Nested
    @DisplayName("Standard path (amount <= 10000)")
    class StandardPathTests {

        @Test
        @DisplayName("Should complete full standard path: start -> validate -> skip fraud -> fork -> stock + payment -> join -> shipping -> confirmation -> end")
        void shouldCompleteStandardPath() {
            // Arrange
            Map<String, Object> variables = new HashMap<>();
            variables.put("orderAmount", 5000L);
            variables.put("orderId", "ORD-001");

            // Act - Step 1: Start process, token advances to Task_ValidateOrder (ServiceTask, WAITING)
            ProcessInstance instance = engine.startProcess("order-processing", variables);

            // Assert - Token should be at Task_ValidateOrder
            assertThat(instance.getState()).isEqualTo(ProcessState.RUNNING);
            assertThat(messageTransport.sendCalls).hasSize(1);
            assertThat(messageTransport.sendCalls.getFirst().topic()).isEqualTo("order.validate");

            // Act - Step 2: Complete validate order task
            // Token should advance through ExclusiveGateway (amount <= 10000 → standard path)
            // → merge gateway → ParallelGateway fork
            // → creates 2 tokens: Task_ReserveStock (ServiceTask) + CallActivity_Payment
            UUID validateTokenId = messageTransport.sendCalls.getFirst().correlationId();
            instance = engine.completeTask(validateTokenId, Map.of());

            // Assert - Should have sent messages for ReserveStock + reached CallActivity_Payment
            // ReserveStock is a ServiceTask so messageTransport gets called
            // CallActivity_Payment emits CallActivityStartedEvent (token goes WAITING)
            assertThat(instance.getState()).isEqualTo(ProcessState.RUNNING);

            // Find waiting/active tokens
            List<Token> activeOrWaitingTokens = instance.getTokens().stream()
                    .filter(t -> t.getState() == TokenState.ACTIVE || t.getState() == TokenState.WAITING)
                    .toList();

            // Should have tokens at ReserveStock and CallActivity_Payment
            assertThat(activeOrWaitingTokens).hasSizeGreaterThanOrEqualTo(2);

            // Act - Step 3: Complete reserve stock
            RecordingMessageTransport.SendCall reserveStockCall = messageTransport.sendCalls.stream()
                    .filter(c -> "warehouse.reserve".equals(c.topic()))
                    .findFirst()
                    .orElseThrow();
            instance = engine.completeTask(reserveStockCall.correlationId(), Map.of("stockReserved", true));

            // Assert - Reserve stock token should move to join gateway, but wait for payment
            assertThat(instance.getState()).isEqualTo(ProcessState.RUNNING);

            // Act - Step 4: Complete call activity payment
            UUID childInstanceId = UUID.randomUUID();
            instance = engine.completeCallActivity(childInstanceId);

            // Assert - After payment completes:
            // - Join gateway fires (both tokens arrived)
            // - Token advances to Task_ArrangeShipping (ServiceTask)
            assertThat(instance.getState()).isEqualTo(ProcessState.RUNNING);

            RecordingMessageTransport.SendCall shippingCall = messageTransport.sendCalls.stream()
                    .filter(c -> "shipping.arrange".equals(c.topic()))
                    .findFirst()
                    .orElseThrow();

            // Act - Step 5: Complete shipping
            instance = engine.completeTask(shippingCall.correlationId(), Map.of());

            // Assert - Token advances to Task_SendConfirmation (ServiceTask)
            RecordingMessageTransport.SendCall confirmationCall = messageTransport.sendCalls.stream()
                    .filter(c -> "notification.order-confirmed".equals(c.topic()))
                    .findFirst()
                    .orElseThrow();

            // Act - Step 6: Complete confirmation
            instance = engine.completeTask(confirmationCall.correlationId(), Map.of());

            // Assert - Process should be COMPLETED
            assertThat(instance.getState()).isEqualTo(ProcessState.COMPLETED);
        }
    }

    @Nested
    @DisplayName("High-value path (amount > 10000)")
    class HighValuePathTests {

        @Test
        @DisplayName("Should route through fraud check for high-value orders")
        void shouldRouteThoughFraudCheckForHighValueOrders() {
            // Arrange
            Map<String, Object> variables = new HashMap<>();
            variables.put("orderAmount", 50000L);
            variables.put("orderId", "ORD-HIGH-001");

            // Act - Step 1: Start process
            ProcessInstance instance = engine.startProcess("order-processing", variables);

            // Assert - At Task_ValidateOrder
            assertThat(messageTransport.sendCalls).hasSize(1);
            assertThat(messageTransport.sendCalls.getFirst().topic()).isEqualTo("order.validate");

            // Act - Step 2: Complete validate order
            // Should go through ExclusiveGateway (amount > 10000 → fraud check path)
            UUID validateTokenId = messageTransport.sendCalls.getFirst().correlationId();
            instance = engine.completeTask(validateTokenId, Map.of());

            // Assert - Should reach Task_FraudCheck (ServiceTask, WAITING)
            RecordingMessageTransport.SendCall fraudCheckCall = messageTransport.sendCalls.stream()
                    .filter(c -> "order.fraud-check".equals(c.topic()))
                    .findFirst()
                    .orElseThrow();
            assertThat(fraudCheckCall).isNotNull();

            // Act - Step 3: Complete fraud check
            // Token should advance through merge gateway → parallel fork
            instance = engine.completeTask(fraudCheckCall.correlationId(), Map.of("fraudCheckPassed", true));

            // Assert - Should have tokens at ReserveStock and CallActivity_Payment
            List<Token> activeOrWaiting = instance.getTokens().stream()
                    .filter(t -> t.getState() == TokenState.ACTIVE || t.getState() == TokenState.WAITING)
                    .toList();
            assertThat(activeOrWaiting).hasSizeGreaterThanOrEqualTo(2);

            // Act - Step 4: Complete reserve stock
            RecordingMessageTransport.SendCall reserveCall = messageTransport.sendCalls.stream()
                    .filter(c -> "warehouse.reserve".equals(c.topic()))
                    .findFirst()
                    .orElseThrow();
            instance = engine.completeTask(reserveCall.correlationId(), Map.of());

            // Act - Step 5: Complete call activity
            instance = engine.completeCallActivity(UUID.randomUUID());

            // Assert - Parallel join should fire, token at shipping
            RecordingMessageTransport.SendCall shippingCall = messageTransport.sendCalls.stream()
                    .filter(c -> "shipping.arrange".equals(c.topic()))
                    .findFirst()
                    .orElseThrow();

            // Act - Step 6: Complete shipping
            instance = engine.completeTask(shippingCall.correlationId(), Map.of());

            // Act - Step 7: Complete confirmation
            RecordingMessageTransport.SendCall confirmCall = messageTransport.sendCalls.stream()
                    .filter(c -> "notification.order-confirmed".equals(c.topic()))
                    .findFirst()
                    .orElseThrow();
            instance = engine.completeTask(confirmCall.correlationId(), Map.of());

            // Assert - Process completed
            assertThat(instance.getState()).isEqualTo(ProcessState.COMPLETED);
        }
    }

    @Nested
    @DisplayName("Event replay")
    class EventReplayTests {

        @Test
        @DisplayName("Should replay events and reconstruct same process state")
        void shouldReplayEventsAndReconstructSameState() {
            // Arrange - Run through standard path until after validation
            Map<String, Object> variables = new HashMap<>();
            variables.put("orderAmount", 5000L);
            variables.put("orderId", "ORD-REPLAY");

            ProcessInstance instance = engine.startProcess("order-processing", variables);
            UUID validateTokenId = messageTransport.sendCalls.getFirst().correlationId();
            instance = engine.completeTask(validateTokenId, Map.of("validated", true));

            // Act - Replay events to rebuild state
            List<ProcessEvent> allEvents = eventStore.getEvents(instance.getId());
            EventApplier eventApplier = new EventApplier();
            ProcessInstanceProjection projection = new ProcessInstanceProjection(eventApplier);
            ProcessInstance replayed = projection.replay(allEvents);

            // Assert - Replayed state should match current state
            assertThat(replayed.getId()).isEqualTo(instance.getId());
            assertThat(replayed.getState()).isEqualTo(instance.getState());
            assertThat(replayed.getTokens()).hasSameSizeAs(instance.getTokens());
            assertThat(replayed.getVariables()).containsEntry("orderAmount", 5000L);
        }

        @Test
        @DisplayName("Should replay completed process and verify COMPLETED state")
        void shouldReplayCompletedProcess() {
            // Arrange - Run full standard path to completion
            Map<String, Object> variables = new HashMap<>();
            variables.put("orderAmount", 5000L);

            ProcessInstance instance = engine.startProcess("order-processing", variables);

            // Complete validate
            UUID validateId = messageTransport.sendCalls.getFirst().correlationId();
            instance = engine.completeTask(validateId, Map.of());

            // Complete reserve stock
            RecordingMessageTransport.SendCall reserveCall = messageTransport.sendCalls.stream()
                    .filter(c -> "warehouse.reserve".equals(c.topic()))
                    .findFirst()
                    .orElseThrow();
            instance = engine.completeTask(reserveCall.correlationId(), Map.of());

            // Complete call activity
            instance = engine.completeCallActivity(UUID.randomUUID());

            // Complete shipping
            RecordingMessageTransport.SendCall shippingCall = messageTransport.sendCalls.stream()
                    .filter(c -> "shipping.arrange".equals(c.topic()))
                    .findFirst()
                    .orElseThrow();
            instance = engine.completeTask(shippingCall.correlationId(), Map.of());

            // Complete confirmation
            RecordingMessageTransport.SendCall confirmCall = messageTransport.sendCalls.stream()
                    .filter(c -> "notification.order-confirmed".equals(c.topic()))
                    .findFirst()
                    .orElseThrow();
            instance = engine.completeTask(confirmCall.correlationId(), Map.of());

            assertThat(instance.getState()).isEqualTo(ProcessState.COMPLETED);

            // Act - Replay all events
            List<ProcessEvent> allEvents = eventStore.getEvents(instance.getId());
            EventApplier eventApplier = new EventApplier();
            ProcessInstanceProjection projection = new ProcessInstanceProjection(eventApplier);
            ProcessInstance replayed = projection.replay(allEvents);

            // Assert
            assertThat(replayed.getState()).isEqualTo(ProcessState.COMPLETED);
            assertThat(replayed.getId()).isEqualTo(instance.getId());

            // All tokens should be completed
            boolean allTokensCompleted = replayed.getTokens().stream()
                    .allMatch(t -> t.getState() == TokenState.COMPLETED);
            assertThat(allTokensCompleted).isTrue();
        }
    }

    @Test
    @DisplayName("Should correctly parse order-processing BPMN")
    void shouldCorrectlyParseOrderProcessBpmn() {
        // Assert
        assertThat(orderProcess.getKey()).isEqualTo("order-processing");
        assertThat(orderProcess.getName()).isEqualTo("Order Processing");
        assertThat(orderProcess.getFlowNodes()).isNotEmpty();
        assertThat(orderProcess.getSequenceFlows()).isNotEmpty();

        // Should contain all expected node types
        long startEvents = orderProcess.getFlowNodes().stream()
                .filter(n -> n.type() == NodeType.START_EVENT).count();
        long endEvents = orderProcess.getFlowNodes().stream()
                .filter(n -> n.type() == NodeType.END_EVENT).count();
        long serviceTasks = orderProcess.getFlowNodes().stream()
                .filter(n -> n.type() == NodeType.SERVICE_TASK).count();
        long exclusiveGateways = orderProcess.getFlowNodes().stream()
                .filter(n -> n.type() == NodeType.EXCLUSIVE_GATEWAY).count();
        long parallelGateways = orderProcess.getFlowNodes().stream()
                .filter(n -> n.type() == NodeType.PARALLEL_GATEWAY).count();
        long callActivities = orderProcess.getFlowNodes().stream()
                .filter(n -> n.type() == NodeType.CALL_ACTIVITY).count();

        assertThat(startEvents).isEqualTo(1);
        assertThat(endEvents).isGreaterThanOrEqualTo(1);
        assertThat(serviceTasks).isGreaterThanOrEqualTo(4);
        assertThat(exclusiveGateways).isGreaterThanOrEqualTo(2);
        assertThat(parallelGateways).isGreaterThanOrEqualTo(2);
        assertThat(callActivities).isEqualTo(1);
    }
}
