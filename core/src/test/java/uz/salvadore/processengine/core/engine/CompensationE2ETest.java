package uz.salvadore.processengine.core.engine;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uz.salvadore.processengine.core.adapter.inmemory.InMemoryEventStore;
import uz.salvadore.processengine.core.domain.enums.NodeType;
import uz.salvadore.processengine.core.domain.enums.ProcessState;
import uz.salvadore.processengine.core.domain.enums.TokenState;
import uz.salvadore.processengine.core.domain.event.CompensationTriggeredEvent;
import uz.salvadore.processengine.core.domain.event.ProcessEvent;
import uz.salvadore.processengine.core.domain.model.ProcessDefinition;
import uz.salvadore.processengine.core.domain.model.ProcessInstance;
import uz.salvadore.processengine.core.domain.model.Token;
import uz.salvadore.processengine.core.engine.condition.SimpleConditionEvaluator;
import uz.salvadore.processengine.core.adapter.inmemory.InMemoryInstanceDefinitionMapping;
import uz.salvadore.processengine.core.adapter.inmemory.InMemoryProcessDefinitionStore;
import uz.salvadore.processengine.core.adapter.inmemory.InMemorySequenceGenerator;
import uz.salvadore.processengine.core.engine.handler.CallActivityHandler;
import uz.salvadore.processengine.core.engine.handler.CompensationBoundaryEventHandler;
import uz.salvadore.processengine.core.engine.handler.EndEventHandler;
import uz.salvadore.processengine.core.engine.handler.ErrorBoundaryEventHandler;
import uz.salvadore.processengine.core.engine.handler.ExclusiveGatewayHandler;
import uz.salvadore.processengine.core.engine.handler.NodeHandler;
import uz.salvadore.processengine.core.engine.handler.ParallelGatewayHandler;
import uz.salvadore.processengine.core.engine.handler.ServiceTaskHandler;
import uz.salvadore.processengine.core.engine.handler.StartEventHandler;
import uz.salvadore.processengine.core.engine.handler.TimerBoundaryEventHandler;
import uz.salvadore.processengine.core.parser.BpmnParser;
import uz.salvadore.processengine.core.port.outgoing.MessageTransport;
import uz.salvadore.processengine.core.port.outgoing.TimerService;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end test for compensation flow using the order-process.bpmn.
 * Verifies that failing a task triggers compensation for previously completed tasks.
 */
class CompensationE2ETest {

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
        InMemoryProcessDefinitionStore definitionStore = new InMemoryProcessDefinitionStore();
        InMemorySequenceGenerator sequenceGenerator = new InMemorySequenceGenerator();
        messageTransport = new RecordingMessageTransport();

        SimpleConditionEvaluator conditionEvaluator = new SimpleConditionEvaluator();

        TimerService noOpTimerService = new TimerService() {
            @Override
            public void schedule(UUID processInstanceId, UUID tokenId, String nodeId,
                                 Duration duration, Consumer<TimerCallback> callback) {
                // no-op
            }

            @Override
            public void cancel(UUID processInstanceId, UUID tokenId) {
                // no-op
            }
        };

        Map<NodeType, NodeHandler> handlers = Map.ofEntries(
                Map.entry(NodeType.START_EVENT, new StartEventHandler(sequenceGenerator)),
                Map.entry(NodeType.END_EVENT, new EndEventHandler(sequenceGenerator)),
                Map.entry(NodeType.SERVICE_TASK, new ServiceTaskHandler(messageTransport)),
                Map.entry(NodeType.EXCLUSIVE_GATEWAY, new ExclusiveGatewayHandler(conditionEvaluator, sequenceGenerator)),
                Map.entry(NodeType.PARALLEL_GATEWAY, new ParallelGatewayHandler(sequenceGenerator)),
                Map.entry(NodeType.CALL_ACTIVITY, new CallActivityHandler(sequenceGenerator)),
                Map.entry(NodeType.TIMER_BOUNDARY, new TimerBoundaryEventHandler(noOpTimerService, sequenceGenerator)),
                Map.entry(NodeType.ERROR_BOUNDARY, new ErrorBoundaryEventHandler(sequenceGenerator)),
                Map.entry(NodeType.COMPENSATION_BOUNDARY, new CompensationBoundaryEventHandler(sequenceGenerator))
        );

        TokenExecutor tokenExecutor = new TokenExecutor(handlers);
        engine = new ProcessEngine(eventStore, definitionStore, tokenExecutor, sequenceGenerator, new InMemoryInstanceDefinitionMapping());

        // Parse and deploy the order-process BPMN
        BpmnParser parser = new BpmnParser();
        InputStream bpmnStream = getClass().getResourceAsStream("/bpmn/order-process.bpmn");
        assertThat(bpmnStream).isNotNull();
        String bpmnXml = new String(bpmnStream.readAllBytes());
        List<ProcessDefinition> definitions = parser.parse(bpmnXml);
        orderProcess = definitions.getFirst();
        engine.deploy(orderProcess);
    }

    @Test
    @DisplayName("Should trigger compensation refund-payment when deliver-order fails")
    void shouldTriggerCompensationWhenDeliverOrderFails() {
        // Arrange
        Map<String, Object> variables = new HashMap<>();
        variables.put("orderId", "ORD-COMP-001");
        variables.put("amount", 15000L);

        // Act - Step 1: Start process, token advances to validate-order (topic: order.validate)
        ProcessInstance instance = engine.startProcess("order-process", variables);

        // Assert - Token should be at validate-order
        assertThat(instance.getState()).isEqualTo(ProcessState.RUNNING);
        assertThat(messageTransport.sendCalls).hasSize(1);
        assertThat(messageTransport.sendCalls.getFirst().topic()).isEqualTo("order.validate");

        // Act - Step 2: Complete validate-order
        // Token advances through parallel gateway fork -> book-order + notify-booking
        UUID validateTokenId = messageTransport.sendCalls.getFirst().correlationId();
        instance = engine.completeTask(validateTokenId, Map.of());

        // Assert - Should have messages for both parallel branches
        RecordingMessageTransport.SendCall bookOrderCall = messageTransport.sendCalls.stream()
                .filter(c -> "order.book".equals(c.topic()))
                .findFirst()
                .orElseThrow();
        RecordingMessageTransport.SendCall notifyBookingCall = messageTransport.sendCalls.stream()
                .filter(c -> "order.notify".equals(c.topic()))
                .findFirst()
                .orElseThrow();

        // Act - Step 3: Complete both parallel tasks
        instance = engine.completeTask(bookOrderCall.correlationId(), Map.of("booked", true));
        instance = engine.completeTask(notifyBookingCall.correlationId(), Map.of("notified", true));

        // Assert - After parallel join, token should reach charge-payment
        RecordingMessageTransport.SendCall chargePaymentCall = messageTransport.sendCalls.stream()
                .filter(c -> "order.payment.charge".equals(c.topic()))
                .findFirst()
                .orElseThrow();

        // Act - Step 4: Complete charge-payment
        instance = engine.completeTask(chargePaymentCall.correlationId(), Map.of("charged", true));

        // Assert - Token should be at deliver-order (WAITING)
        RecordingMessageTransport.SendCall deliverOrderCall = messageTransport.sendCalls.stream()
                .filter(c -> "order.deliver".equals(c.topic()))
                .findFirst()
                .orElseThrow();
        assertThat(instance.getState()).isEqualTo(ProcessState.RUNNING);

        UUID deliverTokenId = deliverOrderCall.correlationId();

        // Act - Step 5: Fail deliver-order (simulating context.error() from worker)
        instance = engine.failTask(deliverTokenId, "RuntimeException", "Unknown error");

        // Assert 1: Compensation task refund-payment was sent to MessageTransport with topic order.payment.refund
        RecordingMessageTransport.SendCall refundCall = messageTransport.sendCalls.stream()
                .filter(c -> "order.payment.refund".equals(c.topic()))
                .findFirst()
                .orElse(null);
        assertThat(refundCall)
                .as("Compensation task refund-payment should be sent to MessageTransport")
                .isNotNull();
        assertThat(refundCall.topic()).isEqualTo("order.payment.refund");

        // Assert 2: Compensation token is registered in instance tokens with state WAITING
        List<Token> compensationTokens = instance.getTokens().stream()
                .filter(t -> "refund-payment".equals(t.getCurrentNodeId()))
                .toList();
        assertThat(compensationTokens)
                .as("Compensation token for refund-payment should exist in instance tokens")
                .hasSize(1);
        assertThat(compensationTokens.getFirst().getState()).isEqualTo(TokenState.WAITING);

        // Assert 3: Process state is ERROR
        assertThat(instance.getState()).isEqualTo(ProcessState.ERROR);

        // Assert 4: CompensationTriggeredEvent is in event store
        List<ProcessEvent> allEvents = eventStore.getEvents(instance.getId());
        List<CompensationTriggeredEvent> compensationEvents = allEvents.stream()
                .filter(CompensationTriggeredEvent.class::isInstance)
                .map(CompensationTriggeredEvent.class::cast)
                .toList();
        assertThat(compensationEvents)
                .as("CompensationTriggeredEvent should be recorded in event store")
                .hasSize(1);
        assertThat(compensationEvents.getFirst().sourceNodeId()).isEqualTo("charge-payment");
        assertThat(compensationEvents.getFirst().compensationTaskId()).isEqualTo("refund-payment");
    }
}
