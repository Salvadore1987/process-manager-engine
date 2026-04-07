package uz.salvadore.processengine.core.engine;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import uz.salvadore.processengine.core.adapter.inmemory.InMemoryActivityLog;
import uz.salvadore.processengine.core.adapter.inmemory.InMemoryChildInstanceMapping;
import uz.salvadore.processengine.core.adapter.inmemory.InMemoryEventStore;
import uz.salvadore.processengine.core.adapter.inmemory.InMemoryInstanceDefinitionMapping;
import uz.salvadore.processengine.core.adapter.inmemory.InMemoryProcessDefinitionStore;
import uz.salvadore.processengine.core.adapter.inmemory.InMemoryProcessInstanceLock;
import uz.salvadore.processengine.core.adapter.inmemory.InMemorySequenceGenerator;
import uz.salvadore.processengine.core.domain.enums.NodeType;
import uz.salvadore.processengine.core.domain.enums.ProcessState;
import uz.salvadore.processengine.core.domain.enums.TokenState;
import uz.salvadore.processengine.core.domain.model.DeploymentBundle;
import uz.salvadore.processengine.core.domain.model.ProcessDefinition;
import uz.salvadore.processengine.core.domain.model.ProcessInstance;
import uz.salvadore.processengine.core.domain.model.Token;
import uz.salvadore.processengine.core.engine.condition.SimpleConditionEvaluator;
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
import uz.salvadore.processengine.core.engine.handler.TimerIntermediateCatchEventHandler;
import uz.salvadore.processengine.core.port.outgoing.MessageTransport;
import uz.salvadore.processengine.core.port.outgoing.TimerService;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Call Activity E2E Tests")
class CallActivityE2ETest {

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
    private InMemorySequenceGenerator sequenceGenerator;
    private InMemoryChildInstanceMapping childInstanceMapping;
    private InMemoryInstanceDefinitionMapping instanceDefinitionMapping;
    private RecordingMessageTransport messageTransport;
    private ProcessEngine engine;

    @BeforeEach
    void setUp() {
        eventStore = new InMemoryEventStore();
        InMemoryProcessDefinitionStore definitionStore = new InMemoryProcessDefinitionStore();
        sequenceGenerator = new InMemorySequenceGenerator();
        messageTransport = new RecordingMessageTransport();
        childInstanceMapping = new InMemoryChildInstanceMapping();
        instanceDefinitionMapping = new InMemoryInstanceDefinitionMapping();

        SimpleConditionEvaluator conditionEvaluator = new SimpleConditionEvaluator();

        TimerService noOpTimerService = new TimerService() {
            @Override
            public void schedule(UUID processInstanceId, UUID tokenId, String nodeId, Duration duration,
                                 Consumer<TimerCallback> callback) {
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
                Map.entry(NodeType.TIMER_INTERMEDIATE_CATCH, new TimerIntermediateCatchEventHandler(noOpTimerService, sequenceGenerator)),
                Map.entry(NodeType.ERROR_BOUNDARY, new ErrorBoundaryEventHandler(sequenceGenerator)),
                Map.entry(NodeType.COMPENSATION_BOUNDARY, new CompensationBoundaryEventHandler(sequenceGenerator))
        );

        TokenExecutor tokenExecutor = new TokenExecutor(handlers);
        engine = new ProcessEngine(eventStore, definitionStore, tokenExecutor, sequenceGenerator,
                instanceDefinitionMapping, childInstanceMapping,
                new InMemoryProcessInstanceLock(), new InMemoryActivityLog(), List.of());

        // Deploy bundle: order-process + charge-payment-subprocess
        String orderBpmn = loadBpmn("bpmn/order-process-with-call-activity.bpmn");
        String chargeBpmn = loadBpmn("bpmn/charge-payment-subprocess.bpmn");

        Map<String, String> bpmnFiles = new LinkedHashMap<>();
        bpmnFiles.put("order-process.bpmn", orderBpmn);
        bpmnFiles.put("charge-payment-subprocess.bpmn", chargeBpmn);

        DeploymentBundle bundle = new DeploymentBundle(bpmnFiles);
        engine.deployBundle(bundle);
    }

    @Nested
    @DisplayName("2.12.1 Full lifecycle — happy path")
    class FullLifecycleTests {

        @Test
        @DisplayName("order-process → validate → parallel(book+notify) → charge-payment(CallActivity) → deliver → end")
        void shouldCompleteFullOrderWithCallActivity() {
            // Arrange & Act — Start order process
            ProcessInstance orderInstance = engine.startProcess("order-process", Map.of());
            assertThat(orderInstance.getState()).isEqualTo(ProcessState.RUNNING);

            // Step 1: Complete validate-order
            RecordingMessageTransport.SendCall validateCall = findSendCall("order.validate");
            ProcessInstance afterValidate = engine.completeTask(validateCall.correlationId(), Map.of());
            assertThat(afterValidate.getState()).isEqualTo(ProcessState.RUNNING);

            // Step 2: Complete book-order and notify-booking (parallel)
            RecordingMessageTransport.SendCall bookCall = findSendCall("order.book");
            RecordingMessageTransport.SendCall notifyCall = findSendCall("order.notify");

            engine.completeTask(bookCall.correlationId(), Map.of());
            ProcessInstance afterParallel = engine.completeTask(notifyCall.correlationId(), Map.of());

            // After parallel join, token reaches charge-payment (CallActivity)
            // CallActivity auto-starts child process charge-payment-subprocess
            // Child process token should be at charge-payment service task (WAITING)
            assertThat(afterParallel.getState()).isEqualTo(ProcessState.RUNNING);

            // The child process should have been started — find its charge-payment task
            RecordingMessageTransport.SendCall chargeCall = findSendCall("order.payment.charge");
            assertThat(chargeCall).isNotNull();

            // Step 3: Complete charge-payment in child subprocess with success
            // Child completes → auto-completes parent → parent advances to deliver-order
            engine.completeTask(chargeCall.correlationId(), Map.of("isPaymentSuccess", true));

            // Step 4: Complete deliver-order in parent
            RecordingMessageTransport.SendCall deliverCall = findSendCall("order.deliver");
            ProcessInstance finalInstance = engine.completeTask(deliverCall.correlationId(), Map.of());

            // Assert — parent process completed
            assertThat(finalInstance.getState()).isEqualTo(ProcessState.COMPLETED);

            // Verify child-parent mapping
            List<UUID> children = childInstanceMapping.getChildren(orderInstance.getId());
            assertThat(children).hasSize(1);

            // Verify child process completed
            UUID childId = children.getFirst();
            ProcessInstance childInstance = engine.getProcessInstance(childId);
            assertThat(childInstance.getState()).isEqualTo(ProcessState.COMPLETED);
            assertThat(childInstance.getParentProcessInstanceId()).isEqualTo(orderInstance.getId());
        }
    }

    @Nested
    @DisplayName("2.12.2 Error in child with ErrorBoundaryEvent")
    class ErrorWithBoundaryTests {

        @Test
        @DisplayName("Child error end → parent routes through error boundary (not applicable in current BPMN — compensation boundary)")
        void shouldHandleChildErrorWithCompensationBoundary() {
            // The order-process.bpmn has CompensationBoundaryEvent on charge-payment, not ErrorBoundaryEvent
            // So child error should trigger compensation path

            // Start and advance to call activity
            ProcessInstance orderInstance = engine.startProcess("order-process", Map.of());
            RecordingMessageTransport.SendCall validateCall = findSendCall("order.validate");
            engine.completeTask(validateCall.correlationId(), Map.of());

            RecordingMessageTransport.SendCall bookCall = findSendCall("order.book");
            RecordingMessageTransport.SendCall notifyCall = findSendCall("order.notify");
            engine.completeTask(bookCall.correlationId(), Map.of());
            engine.completeTask(notifyCall.correlationId(), Map.of());

            // Child process started, charge-payment task waiting
            RecordingMessageTransport.SendCall chargeCall = findSendCall("order.payment.charge");

            // Fail the charge task in child process — no error boundary on charge-payment task itself
            // This should cause child process to error, then propagate to parent
            engine.failTask(chargeCall.correlationId(), "PAYMENT_FAILED", "Card declined");

            // Verify child is in ERROR state
            List<UUID> children = childInstanceMapping.getChildren(orderInstance.getId());
            assertThat(children).hasSize(1);
            ProcessInstance childInstance = engine.getProcessInstance(children.getFirst());
            assertThat(childInstance.getState()).isEqualTo(ProcessState.ERROR);

            // Parent should have received error propagation
            // Since there's no ErrorBoundaryEvent on charge-payment CallActivity (only CompensationBoundary),
            // parent triggers compensation and goes to ERROR
            ProcessInstance parentInstance = engine.getProcessInstance(orderInstance.getId());
            assertThat(parentInstance.getState()).isEqualTo(ProcessState.ERROR);

            // Compensation task (refund-payment) should have been triggered
            assertThat(messageTransport.sendCalls).anyMatch(c -> c.topic().equals("order.payment.refund"));
        }
    }

    @Nested
    @DisplayName("2.12.5 Retry loop in subprocess")
    class RetryLoopTests {

        @Test
        @DisplayName("charge(false) → check-status(false) → timer → charge(true) → end → parent continues")
        void shouldHandleRetryLoopInSubprocess() {
            // Start and advance to call activity
            ProcessInstance orderInstance = engine.startProcess("order-process", Map.of());
            RecordingMessageTransport.SendCall validateCall = findSendCall("order.validate");
            engine.completeTask(validateCall.correlationId(), Map.of());

            RecordingMessageTransport.SendCall bookCall = findSendCall("order.book");
            RecordingMessageTransport.SendCall notifyCall = findSendCall("order.notify");
            engine.completeTask(bookCall.correlationId(), Map.of());
            engine.completeTask(notifyCall.correlationId(), Map.of());

            // Child process: charge-payment
            RecordingMessageTransport.SendCall chargeCall = findSendCall("order.payment.charge");

            // Step 1: Complete charge with failure → gateway → check-status
            engine.completeTask(chargeCall.correlationId(), Map.of("isPaymentSuccess", false));

            RecordingMessageTransport.SendCall statusCall = findSendCall("order.payment.status");
            assertThat(statusCall).isNotNull();

            // Step 2: Complete check-status with failure → gateway → timer
            engine.completeTask(statusCall.correlationId(), Map.of("isPaymentSuccess", false));

            // Find the child process instance to complete the timer
            List<UUID> children = childInstanceMapping.getChildren(orderInstance.getId());
            UUID childId = children.getFirst();
            ProcessInstance childInstance = engine.getProcessInstance(childId);

            Token timerToken = childInstance.getTokens().stream()
                    .filter(t -> t.getState() == TokenState.WAITING)
                    .findFirst()
                    .orElseThrow();
            assertThat(timerToken.getCurrentNodeId()).isEqualTo("charge-wait");

            // Step 3: Complete timer → charge-payment again
            engine.completeTimer(childId, timerToken.getId(), "charge-wait");

            // Find the retry charge-payment call
            List<RecordingMessageTransport.SendCall> chargeCalls = messageTransport.sendCalls.stream()
                    .filter(c -> c.topic().equals("order.payment.charge"))
                    .toList();
            assertThat(chargeCalls).hasSize(2);

            RecordingMessageTransport.SendCall retryChargeCall = chargeCalls.get(1);

            // Step 4: Complete retry with success → child completes → parent auto-completes → deliver-order
            engine.completeTask(retryChargeCall.correlationId(), Map.of("isPaymentSuccess", true));

            // Step 5: Complete deliver-order in parent
            RecordingMessageTransport.SendCall deliverCall = findSendCall("order.deliver");
            ProcessInstance finalInstance = engine.completeTask(deliverCall.correlationId(), Map.of());

            // Assert
            assertThat(finalInstance.getState()).isEqualTo(ProcessState.COMPLETED);

            ProcessInstance finalChild = engine.getProcessInstance(childId);
            assertThat(finalChild.getState()).isEqualTo(ProcessState.COMPLETED);
        }
    }

    @Nested
    @DisplayName("2.12.6 Deploy bundle with missing subprocess")
    class DeployValidationTests {

        @Test
        @DisplayName("Should fail deployment when subprocess BPMN is missing from bundle")
        void shouldFailDeployWhenSubprocessMissing() {
            // Arrange
            String orderBpmn = loadBpmn("bpmn/order-process-with-call-activity.bpmn");

            Map<String, String> bpmnFiles = new LinkedHashMap<>();
            bpmnFiles.put("order-process.bpmn", orderBpmn);
            // Missing charge-payment-subprocess.bpmn

            DeploymentBundle bundle = new DeploymentBundle(bpmnFiles);

            // Act & Assert
            assertThatThrownBy(() -> engine.deployBundle(bundle))
                    .isInstanceOf(uz.salvadore.processengine.core.domain.exception.CallActivitySubprocessNotFoundException.class)
                    .hasMessageContaining("charge-payment-subprocess");
        }
    }

    private RecordingMessageTransport.SendCall findSendCall(String topic) {
        return messageTransport.sendCalls.stream()
                .filter(c -> c.topic().equals(topic))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "No send call found for topic: " + topic + ". Available: "
                                + messageTransport.sendCalls.stream()
                                .map(RecordingMessageTransport.SendCall::topic).toList()));
    }

    private static String loadBpmn(String resourcePath) {
        try (InputStream is = CallActivityE2ETest.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IllegalArgumentException("Resource not found: " + resourcePath);
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load BPMN resource: " + resourcePath, e);
        }
    }
}
