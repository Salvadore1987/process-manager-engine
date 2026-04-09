package uz.salvadore.processengine.core.engine;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uz.salvadore.processengine.core.adapter.inmemory.InMemoryActivityLog;
import uz.salvadore.processengine.core.adapter.inmemory.InMemoryBusinessKeyMapping;
import uz.salvadore.processengine.core.adapter.inmemory.InMemoryEventStore;
import uz.salvadore.processengine.core.adapter.inmemory.InMemoryInstanceDefinitionMapping;
import uz.salvadore.processengine.core.adapter.inmemory.InMemoryProcessDefinitionStore;
import uz.salvadore.processengine.core.adapter.inmemory.InMemoryProcessInstanceLock;
import uz.salvadore.processengine.core.adapter.inmemory.InMemorySequenceGenerator;
import uz.salvadore.processengine.core.domain.enums.NodeType;
import uz.salvadore.processengine.core.domain.enums.ProcessState;
import uz.salvadore.processengine.core.domain.enums.TokenState;
import uz.salvadore.processengine.core.domain.model.EndEvent;
import uz.salvadore.processengine.core.domain.model.ProcessDefinition;
import uz.salvadore.processengine.core.domain.model.ProcessInstance;
import uz.salvadore.processengine.core.domain.model.SequenceFlow;
import uz.salvadore.processengine.core.domain.model.ServiceTask;
import uz.salvadore.processengine.core.domain.model.StartEvent;
import uz.salvadore.processengine.core.domain.model.TimerDefinition;
import uz.salvadore.processengine.core.domain.model.TimerIntermediateCatchEvent;
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
import uz.salvadore.processengine.core.port.outgoing.MessageTransport;
import uz.salvadore.processengine.core.port.outgoing.TimerService;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Timer Intermediate Catch Event E2E")
class TimerIntermediateCatchE2ETest {

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
    private InMemoryProcessDefinitionStore definitionStore;
    private InMemorySequenceGenerator sequenceGenerator;
    private RecordingMessageTransport messageTransport;
    private ProcessEngine engine;

    @BeforeEach
    void setUp() {
        eventStore = new InMemoryEventStore();
        definitionStore = new InMemoryProcessDefinitionStore();
        sequenceGenerator = new InMemorySequenceGenerator();
        messageTransport = new RecordingMessageTransport();

        SimpleConditionEvaluator conditionEvaluator = new SimpleConditionEvaluator();

        TimerService noOpTimerService = new TimerService() {
            @Override
            public void schedule(UUID processInstanceId, UUID tokenId, String nodeId, Duration duration,
                                 Consumer<TimerCallback> callback) {
                // no-op for test
            }

            @Override
            public void cancel(UUID processInstanceId, UUID tokenId) {
                // no-op for test
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
                new InMemoryInstanceDefinitionMapping(), new InMemoryProcessInstanceLock(), new InMemoryBusinessKeyMapping(), new InMemoryActivityLog());
    }

    /**
     * Process: Start -> Timer(PT5S) -> ServiceTask -> End
     */
    private ProcessDefinition createTimerProcess() {
        StartEvent startEvent = new StartEvent("start1", "Start", List.of(), List.of("flow1"));
        TimerIntermediateCatchEvent timerNode = new TimerIntermediateCatchEvent(
                "timer1", "Wait 5s", List.of("flow1"), List.of("flow2"),
                new TimerDefinition(TimerDefinition.TimerType.DURATION, "PT5S"));
        ServiceTask task = new ServiceTask("task1", "Do Work",
                List.of("flow2"), List.of("flow3"), "work.topic", 0, Duration.ZERO);
        EndEvent endEvent = new EndEvent("end1", "End", List.of("flow3"), List.of(), null);

        SequenceFlow flow1 = new SequenceFlow("flow1", "start1", "timer1", null);
        SequenceFlow flow2 = new SequenceFlow("flow2", "timer1", "task1", null);
        SequenceFlow flow3 = new SequenceFlow("flow3", "task1", "end1", null);

        return ProcessDefinition.create("timer-process", 1, "Timer Process", "<xml/>",
                List.of(startEvent, timerNode, task, endEvent), List.of(flow1, flow2, flow3));
    }

    @Test
    @DisplayName("Start process: token reaches Timer and goes WAITING")
    void shouldReachTimerAndWait() {
        // Arrange
        ProcessDefinition definition = createTimerProcess();
        engine.deploy(definition);

        // Act
        ProcessInstance instance = engine.startProcess("timer-process", "test-biz-key", Map.of());

        // Assert
        assertThat(instance.getState()).isEqualTo(ProcessState.RUNNING);
        assertThat(instance.getTokens()).hasSize(1);

        Token waitingToken = instance.getTokens().getFirst();
        assertThat(waitingToken.getCurrentNodeId()).isEqualTo("timer1");
        assertThat(waitingToken.getState()).isEqualTo(TokenState.WAITING);

        // No messages sent yet — timer hasn't fired
        assertThat(messageTransport.sendCalls).isEmpty();
    }

    @Test
    @DisplayName("Complete timer: token advances to ServiceTask (WAITING for external)")
    void shouldAdvanceToServiceTaskAfterTimerComplete() {
        // Arrange
        ProcessDefinition definition = createTimerProcess();
        engine.deploy(definition);
        ProcessInstance started = engine.startProcess("timer-process", "test-biz-key", Map.of());

        Token waitingToken = started.getTokens().stream()
                .filter(t -> t.getState() == TokenState.WAITING)
                .findFirst()
                .orElseThrow();

        // Act
        ProcessInstance afterTimer = engine.completeTimer(
                started.getId(), waitingToken.getId(), "timer1");

        // Assert
        assertThat(afterTimer.getState()).isEqualTo(ProcessState.RUNNING);

        Token taskToken = afterTimer.getTokens().stream()
                .filter(t -> t.getState() == TokenState.WAITING)
                .findFirst()
                .orElseThrow();
        assertThat(taskToken.getCurrentNodeId()).isEqualTo("task1");

        // ServiceTask should have sent a message
        assertThat(messageTransport.sendCalls).hasSize(1);
        assertThat(messageTransport.sendCalls.getFirst().topic()).isEqualTo("work.topic");
    }

    @Test
    @DisplayName("Full flow: Start -> Timer -> ServiceTask -> End = COMPLETED")
    void shouldCompleteFullTimerFlow() {
        // Arrange
        ProcessDefinition definition = createTimerProcess();
        engine.deploy(definition);
        ProcessInstance started = engine.startProcess("timer-process", "test-biz-key", Map.of());

        Token timerToken = started.getTokens().stream()
                .filter(t -> t.getState() == TokenState.WAITING)
                .findFirst()
                .orElseThrow();

        ProcessInstance afterTimer = engine.completeTimer(
                started.getId(), timerToken.getId(), "timer1");

        Token taskToken = afterTimer.getTokens().stream()
                .filter(t -> t.getState() == TokenState.WAITING)
                .findFirst()
                .orElseThrow();

        // Act
        ProcessInstance completed = engine.completeTask(taskToken.getId(), Map.of("result", "ok"));

        // Assert
        assertThat(completed.getState()).isEqualTo(ProcessState.COMPLETED);
    }
}
