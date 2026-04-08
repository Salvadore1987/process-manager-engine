package uz.salvadore.processengine.core.engine;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import uz.salvadore.processengine.core.adapter.inmemory.InMemoryActivityLog;
import uz.salvadore.processengine.core.adapter.inmemory.InMemoryEventStore;
import uz.salvadore.processengine.core.adapter.inmemory.InMemoryInstanceDefinitionMapping;
import uz.salvadore.processengine.core.adapter.inmemory.InMemoryProcessDefinitionStore;
import uz.salvadore.processengine.core.adapter.inmemory.InMemoryProcessInstanceLock;
import uz.salvadore.processengine.core.adapter.inmemory.InMemorySequenceGenerator;
import uz.salvadore.processengine.core.domain.enums.NodeType;
import uz.salvadore.processengine.core.domain.enums.ProcessState;
import uz.salvadore.processengine.core.domain.enums.TokenState;
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
import uz.salvadore.processengine.core.engine.handler.TimerIntermediateCatchEventHandler;
import uz.salvadore.processengine.core.parser.BpmnParser;
import uz.salvadore.processengine.core.port.outgoing.MessageTransport;
import uz.salvadore.processengine.core.port.outgoing.TimerService;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("charge-payment-subprocess.bpmn E2E Tests")
class ChargePaymentSubprocessE2ETest {

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
    private RecordingMessageTransport messageTransport;
    private ProcessEngine engine;

    @BeforeEach
    void setUp() {
        eventStore = new InMemoryEventStore();
        InMemoryProcessDefinitionStore definitionStore = new InMemoryProcessDefinitionStore();
        sequenceGenerator = new InMemorySequenceGenerator();
        messageTransport = new RecordingMessageTransport();

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
                Map.entry(NodeType.TIMER_INTERMEDIATE_CATCH, new TimerIntermediateCatchEventHandler(noOpTimerService)),
                Map.entry(NodeType.ERROR_BOUNDARY, new ErrorBoundaryEventHandler(sequenceGenerator)),
                Map.entry(NodeType.COMPENSATION_BOUNDARY, new CompensationBoundaryEventHandler(sequenceGenerator))
        );

        TokenExecutor tokenExecutor = new TokenExecutor(handlers);
        engine = new ProcessEngine(eventStore, definitionStore, tokenExecutor, sequenceGenerator,
                new InMemoryInstanceDefinitionMapping(), new InMemoryProcessInstanceLock(), new InMemoryActivityLog());

        // Parse and deploy the real BPMN file
        BpmnParser parser = new BpmnParser();
        String bpmnXml = loadBpmn("bpmn/charge-payment-subprocess.bpmn");
        List<ProcessDefinition> definitions = parser.parse(bpmnXml);
        for (ProcessDefinition definition : definitions) {
            engine.deploy(definition);
        }
    }

    @Nested
    @DisplayName("Direct success path")
    class DirectSuccessTests {

        @Test
        @DisplayName("Start -> charge-payment -> isPaymentSuccess=true -> EndEvent = COMPLETED")
        void shouldCompleteOnDirectPaymentSuccess() {
            // Arrange
            ProcessInstance started = engine.startProcess("charge-payment-subprocess", Map.of());
            assertThat(started.getState()).isEqualTo(ProcessState.RUNNING);

            // Token should be at charge-payment (WAITING for external worker)
            Token chargeToken = findWaitingToken(started);
            assertThat(chargeToken.getCurrentNodeId()).isEqualTo("charge-payment");
            assertThat(messageTransport.sendCalls).anyMatch(c -> c.topic().equals("order.payment.charge"));

            // Act — complete charge-payment with success
            ProcessInstance completed = engine.completeTask(
                    chargeToken.getId(), Map.of("isPaymentSuccess", true));

            // Assert
            assertThat(completed.getState()).isEqualTo(ProcessState.COMPLETED);
        }
    }

    @Nested
    @DisplayName("Success after status check")
    class SuccessAfterStatusCheckTests {

        @Test
        @DisplayName("charge(false) -> default(check-status) -> isPaymentSuccess=true -> EndEvent = COMPLETED")
        void shouldCompleteAfterStatusCheck() {
            // Arrange
            ProcessInstance started = engine.startProcess("charge-payment-subprocess", Map.of());
            Token chargeToken = findWaitingToken(started);
            assertThat(chargeToken.getCurrentNodeId()).isEqualTo("charge-payment");

            // Act — complete charge-payment with failure => gateway default => check-status
            ProcessInstance afterCharge = engine.completeTask(
                    chargeToken.getId(), Map.of("isPaymentSuccess", false));

            // Assert — token should be at check-status
            assertThat(afterCharge.getState()).isEqualTo(ProcessState.RUNNING);
            Token statusToken = findWaitingToken(afterCharge);
            assertThat(statusToken.getCurrentNodeId()).isEqualTo("check-status");
            assertThat(messageTransport.sendCalls).anyMatch(c -> c.topic().equals("order.payment.status"));

            // Act — complete check-status with success => gateway => EndEvent
            ProcessInstance completed = engine.completeTask(
                    statusToken.getId(), Map.of("isPaymentSuccess", true));

            // Assert
            assertThat(completed.getState()).isEqualTo(ProcessState.COMPLETED);
        }
    }

    @Nested
    @DisplayName("Retry with timer")
    class RetryWithTimerTests {

        @Test
        @DisplayName("charge(false) -> check-status(false) -> timer(WAITING) -> completeTimer -> charge(true) = COMPLETED")
        void shouldRetryViaTimerAndComplete() {
            // Arrange — start process
            ProcessInstance started = engine.startProcess("charge-payment-subprocess", Map.of());
            Token chargeToken = findWaitingToken(started);
            assertThat(chargeToken.getCurrentNodeId()).isEqualTo("charge-payment");

            // Step 1: complete charge-payment with failure
            ProcessInstance afterCharge = engine.completeTask(
                    chargeToken.getId(), Map.of("isPaymentSuccess", false));
            Token statusToken = findWaitingToken(afterCharge);
            assertThat(statusToken.getCurrentNodeId()).isEqualTo("check-status");

            // Step 2: complete check-status with failure => gateway default => timer
            ProcessInstance afterStatus = engine.completeTask(
                    statusToken.getId(), Map.of("isPaymentSuccess", false));

            // Assert — token should be at charge-wait timer (WAITING)
            assertThat(afterStatus.getState()).isEqualTo(ProcessState.RUNNING);
            Token timerToken = findWaitingToken(afterStatus);
            assertThat(timerToken.getCurrentNodeId()).isEqualTo("charge-wait");

            // Step 3: complete timer => token advances to charge-payment again
            ProcessInstance afterTimer = engine.completeTimer(
                    afterStatus.getId(), timerToken.getId(), "charge-wait");

            assertThat(afterTimer.getState()).isEqualTo(ProcessState.RUNNING);
            Token retryChargeToken = findWaitingToken(afterTimer);
            assertThat(retryChargeToken.getCurrentNodeId()).isEqualTo("charge-payment");

            // Step 4: complete charge-payment with success => gateway => EndEvent
            ProcessInstance completed = engine.completeTask(
                    retryChargeToken.getId(), Map.of("isPaymentSuccess", true));

            // Assert
            assertThat(completed.getState()).isEqualTo(ProcessState.COMPLETED);
        }
    }

    private Token findWaitingToken(ProcessInstance instance) {
        return instance.getTokens().stream()
                .filter(t -> t.getState() == TokenState.WAITING)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "No WAITING token found. Tokens: " + instance.getTokens()));
    }

    private static String loadBpmn(String resourcePath) {
        try (InputStream is = ChargePaymentSubprocessE2ETest.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IllegalArgumentException("Resource not found: " + resourcePath);
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load BPMN resource: " + resourcePath, e);
        }
    }
}
