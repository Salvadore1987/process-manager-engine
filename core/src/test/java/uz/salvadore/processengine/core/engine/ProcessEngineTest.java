package uz.salvadore.processengine.core.engine;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import uz.salvadore.processengine.core.adapter.inmemory.InMemoryEventStore;
import uz.salvadore.processengine.core.domain.enums.NodeType;
import uz.salvadore.processengine.core.domain.enums.ProcessState;
import uz.salvadore.processengine.core.domain.enums.TokenState;
import uz.salvadore.processengine.core.domain.event.CompensationTriggeredEvent;
import uz.salvadore.processengine.core.domain.event.ProcessErrorEvent;
import uz.salvadore.processengine.core.domain.event.ProcessEvent;
import uz.salvadore.processengine.core.domain.model.CallActivity;
import uz.salvadore.processengine.core.domain.model.CompensationBoundaryEvent;
import uz.salvadore.processengine.core.domain.model.EndEvent;
import uz.salvadore.processengine.core.domain.model.ErrorBoundaryEvent;
import uz.salvadore.processengine.core.domain.model.ProcessDefinition;
import uz.salvadore.processengine.core.domain.model.ProcessInstance;
import uz.salvadore.processengine.core.domain.model.SequenceFlow;
import uz.salvadore.processengine.core.domain.model.ServiceTask;
import uz.salvadore.processengine.core.domain.model.StartEvent;
import uz.salvadore.processengine.core.domain.model.Token;
import uz.salvadore.processengine.core.engine.condition.SimpleConditionEvaluator;
import uz.salvadore.processengine.core.adapter.inmemory.InMemoryInstanceDefinitionMapping;
import uz.salvadore.processengine.core.adapter.inmemory.InMemoryProcessDefinitionStore;
import uz.salvadore.processengine.core.adapter.inmemory.InMemoryActivityLog;
import uz.salvadore.processengine.core.adapter.inmemory.InMemoryProcessInstanceLock;
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
import uz.salvadore.processengine.core.port.outgoing.MessageTransport;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProcessEngineTest {

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

        Map<NodeType, NodeHandler> handlers = Map.ofEntries(
                Map.entry(NodeType.START_EVENT, new StartEventHandler(sequenceGenerator)),
                Map.entry(NodeType.END_EVENT, new EndEventHandler(sequenceGenerator)),
                Map.entry(NodeType.SERVICE_TASK, new ServiceTaskHandler(messageTransport)),
                Map.entry(NodeType.EXCLUSIVE_GATEWAY, new ExclusiveGatewayHandler(conditionEvaluator, sequenceGenerator)),
                Map.entry(NodeType.PARALLEL_GATEWAY, new ParallelGatewayHandler(sequenceGenerator)),
                Map.entry(NodeType.CALL_ACTIVITY, new CallActivityHandler(sequenceGenerator)),
                Map.entry(NodeType.ERROR_BOUNDARY, new ErrorBoundaryEventHandler(sequenceGenerator)),
                Map.entry(NodeType.COMPENSATION_BOUNDARY, new CompensationBoundaryEventHandler(sequenceGenerator))
        );

        TokenExecutor tokenExecutor = new TokenExecutor(handlers);
        engine = new ProcessEngine(eventStore, definitionStore, tokenExecutor, sequenceGenerator, new InMemoryInstanceDefinitionMapping(), new InMemoryProcessInstanceLock(), new InMemoryActivityLog());
    }

    private ProcessDefinition createSimpleLinearProcess() {
        StartEvent startEvent = new StartEvent("start1", "Start", List.of(), List.of("flow1"));
        ServiceTask task = new ServiceTask("task1", "Do Work",
                List.of("flow1"), List.of("flow2"), "work.topic", 0, Duration.ZERO);
        EndEvent endEvent = new EndEvent("end1", "End", List.of("flow2"), List.of(), null);
        SequenceFlow flow1 = new SequenceFlow("flow1", "start1", "task1", null);
        SequenceFlow flow2 = new SequenceFlow("flow2", "task1", "end1", null);

        return ProcessDefinition.create("simple-process", 1, "Simple Process", "<xml/>",
                List.of(startEvent, task, endEvent), List.of(flow1, flow2));
    }

    @Nested
    @DisplayName("deploy()")
    class DeployTests {

        @Test
        @DisplayName("Should deploy process definition to repository")
        void shouldDeployProcessDefinition() {
            // Arrange
            ProcessDefinition definition = createSimpleLinearProcess();

            // Act
            engine.deploy(definition);

            // Assert
            assertThat(definitionStore.getByKey("simple-process")).isPresent();
        }

        @Test
        @DisplayName("Should reject deploy when ProcessDefinition contains a CallActivity")
        void shouldRejectDeployWithCallActivity() {
            // Arrange
            StartEvent startEvent = new StartEvent("start1", "Start", List.of(), List.of("flow1"));
            CallActivity callActivity = new CallActivity("call1", "Call Sub",
                    List.of("flow1"), List.of("flow2"), "sub-process");
            EndEvent endEvent = new EndEvent("end1", "End", List.of("flow2"), List.of(), null);
            SequenceFlow flow1 = new SequenceFlow("flow1", "start1", "call1", null);
            SequenceFlow flow2 = new SequenceFlow("flow2", "call1", "end1", null);

            ProcessDefinition definition = ProcessDefinition.create("call-process", 1, "Call", "<xml/>",
                    List.of(startEvent, callActivity, endEvent), List.of(flow1, flow2));

            // Act & Assert
            assertThatThrownBy(() -> engine.deploy(definition))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Call Activity")
                    .hasMessageContaining("deployBundle");
        }
    }

    @Nested
    @DisplayName("startProcess()")
    class StartProcessTests {

        @Test
        @DisplayName("Should start process and advance to first ServiceTask (WAITING)")
        void shouldStartProcessAndAdvanceToServiceTask() {
            // Arrange
            ProcessDefinition definition = createSimpleLinearProcess();
            engine.deploy(definition);

            // Act
            ProcessInstance instance = engine.startProcess("simple-process", Map.of("key", "value"));

            // Assert
            assertThat(instance).isNotNull();
            assertThat(instance.getState()).isEqualTo(ProcessState.RUNNING);
            assertThat(instance.getVariables()).containsEntry("key", "value");

            // Token should be at task1 (ServiceTask causes WAITING via auto-advance logic)
            // The engine auto-advances through StartEvent, so the token arrives at task1
            assertThat(instance.getTokens()).isNotEmpty();
        }

        @Test
        @DisplayName("Should emit message via MessageTransport when reaching ServiceTask")
        void shouldEmitMessageWhenReachingServiceTask() {
            // Arrange
            ProcessDefinition definition = createSimpleLinearProcess();
            engine.deploy(definition);

            // Act
            engine.startProcess("simple-process", Map.of("orderId", "ORD-001"));

            // Assert
            assertThat(messageTransport.sendCalls).hasSize(1);
            assertThat(messageTransport.sendCalls.getFirst().topic()).isEqualTo("work.topic");
        }

        @Test
        @DisplayName("Should throw when definition not found")
        void shouldThrowWhenDefinitionNotFound() {
            // Act & Assert
            assertThatThrownBy(() -> engine.startProcess("non-existent", Map.of()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Process definition not found");
        }

        @Test
        @DisplayName("Should handle null variables by using empty map")
        void shouldHandleNullVariables() {
            // Arrange
            ProcessDefinition definition = createSimpleLinearProcess();
            engine.deploy(definition);

            // Act
            ProcessInstance instance = engine.startProcess("simple-process", null);

            // Assert
            assertThat(instance).isNotNull();
            assertThat(instance.getVariables()).isEmpty();
        }

        @Test
        @DisplayName("Should store events in event store")
        void shouldStoreEventsInEventStore() {
            // Arrange
            ProcessDefinition definition = createSimpleLinearProcess();
            engine.deploy(definition);

            // Act
            ProcessInstance instance = engine.startProcess("simple-process", Map.of());

            // Assert
            List<ProcessEvent> storedEvents = eventStore.getEvents(instance.getId());
            assertThat(storedEvents).isNotEmpty();
            // At minimum: ProcessStartedEvent + TokenMovedEvent (StartEvent → task1)
            assertThat(storedEvents.size()).isGreaterThanOrEqualTo(2);
        }
    }

    @Nested
    @DisplayName("completeTask()")
    class CompleteTaskTests {

        @Test
        @DisplayName("Should complete task and advance token through EndEvent to COMPLETED state")
        void shouldCompleteTaskAndAdvanceToEnd() {
            // Arrange
            ProcessDefinition definition = createSimpleLinearProcess();
            engine.deploy(definition);
            ProcessInstance started = engine.startProcess("simple-process", Map.of());

            // Find the waiting token
            Token waitingToken = started.getTokens().stream()
                    .filter(t -> t.getState() == TokenState.ACTIVE || t.getState() == TokenState.WAITING)
                    .findFirst()
                    .orElseThrow();

            // Act
            ProcessInstance completed = engine.completeTask(waitingToken.getId(), Map.of("result", "success"));

            // Assert
            assertThat(completed.getState()).isEqualTo(ProcessState.COMPLETED);
        }

        @Test
        @DisplayName("Should merge task result variables into process instance")
        void shouldMergeTaskResultVariables() {
            // Arrange
            ProcessDefinition definition = createSimpleLinearProcess();
            engine.deploy(definition);
            ProcessInstance started = engine.startProcess("simple-process", Map.of("initial", "data"));

            Token waitingToken = started.getTokens().stream()
                    .filter(t -> t.getState() == TokenState.ACTIVE || t.getState() == TokenState.WAITING)
                    .findFirst()
                    .orElseThrow();

            // Act
            ProcessInstance completed = engine.completeTask(waitingToken.getId(), Map.of("taskResult", "done"));

            // Assert
            assertThat(completed.getVariables()).containsEntry("initial", "data");
            assertThat(completed.getVariables()).containsEntry("taskResult", "done");
        }

        @Test
        @DisplayName("Should throw when token not found")
        void shouldThrowWhenTokenNotFound() {
            // Arrange
            ProcessDefinition definition = createSimpleLinearProcess();
            engine.deploy(definition);
            engine.startProcess("simple-process", Map.of());

            // Act & Assert
            assertThatThrownBy(() -> engine.completeTask(UUID.randomUUID(), Map.of()))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Nested
    @DisplayName("completeCallActivity()")
    class CompleteCallActivityTests {

        @Test
        @DisplayName("Should complete parent call activity token and advance")
        void shouldCompleteParentCallActivityToken() {
            // Arrange
            StartEvent startEvent = new StartEvent("start1", "Start", List.of(), List.of("flow1"));
            CallActivity callActivity = new CallActivity("call1", "Payment",
                    List.of("flow1"), List.of("flow2"), "payment-processing");
            EndEvent endEvent = new EndEvent("end1", "End", List.of("flow2"), List.of(), null);
            SequenceFlow flow1 = new SequenceFlow("flow1", "start1", "call1", null);
            SequenceFlow flow2 = new SequenceFlow("flow2", "call1", "end1", null);

            ProcessDefinition definition = ProcessDefinition.create("call-process", 1, "Call", "<xml/>",
                    List.of(startEvent, callActivity, endEvent), List.of(flow1, flow2));
            engine.deploy(definition);

            ProcessInstance started = engine.startProcess("call-process", Map.of());
            // After start, token should be at CallActivity (WAITING via CallActivityStartedEvent)

            // Act
            UUID childInstanceId = UUID.randomUUID();
            ProcessInstance completed = engine.completeCallActivity(childInstanceId);

            // Assert
            assertThat(completed.getState()).isEqualTo(ProcessState.COMPLETED);
        }

        @Test
        @DisplayName("Should throw when no parent found for child instance")
        void shouldThrowWhenNoParentFound() {
            // Act & Assert
            assertThatThrownBy(() -> engine.completeCallActivity(UUID.randomUUID()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("No parent process found");
        }
    }

    @Nested
    @DisplayName("suspendProcess() / resumeProcess()")
    class SuspendResumeTests {

        @Test
        @DisplayName("Should suspend a running process")
        void shouldSuspendRunningProcess() {
            // Arrange
            ProcessDefinition definition = createSimpleLinearProcess();
            engine.deploy(definition);
            ProcessInstance started = engine.startProcess("simple-process", Map.of());

            // Act
            ProcessInstance suspended = engine.suspendProcess(started.getId());

            // Assert
            assertThat(suspended.getState()).isEqualTo(ProcessState.SUSPENDED);
        }

        @Test
        @DisplayName("Should resume a suspended process")
        void shouldResumeSuspendedProcess() {
            // Arrange
            ProcessDefinition definition = createSimpleLinearProcess();
            engine.deploy(definition);
            ProcessInstance started = engine.startProcess("simple-process", Map.of());
            engine.suspendProcess(started.getId());

            // Act
            ProcessInstance resumed = engine.resumeProcess(started.getId());

            // Assert
            assertThat(resumed.getState()).isEqualTo(ProcessState.RUNNING);
        }

        @Test
        @DisplayName("Should throw when suspending non-running process")
        void shouldThrowWhenSuspendingNonRunningProcess() {
            // Arrange
            ProcessDefinition definition = createSimpleLinearProcess();
            engine.deploy(definition);
            ProcessInstance started = engine.startProcess("simple-process", Map.of());
            engine.suspendProcess(started.getId());

            // Act & Assert
            assertThatThrownBy(() -> engine.suspendProcess(started.getId()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Cannot suspend process in state SUSPENDED");
        }

        @Test
        @DisplayName("Should throw when resuming non-suspended process")
        void shouldThrowWhenResumingNonSuspendedProcess() {
            // Arrange
            ProcessDefinition definition = createSimpleLinearProcess();
            engine.deploy(definition);
            ProcessInstance started = engine.startProcess("simple-process", Map.of());

            // Act & Assert
            assertThatThrownBy(() -> engine.resumeProcess(started.getId()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Cannot resume process in state RUNNING");
        }
    }

    @Nested
    @DisplayName("terminateProcess()")
    class TerminateTests {

        @Test
        @DisplayName("Should terminate a running process")
        void shouldTerminateRunningProcess() {
            // Arrange
            ProcessDefinition definition = createSimpleLinearProcess();
            engine.deploy(definition);
            ProcessInstance started = engine.startProcess("simple-process", Map.of());

            // Act
            ProcessInstance terminated = engine.terminateProcess(started.getId());

            // Assert
            assertThat(terminated.getState()).isEqualTo(ProcessState.ERROR);
        }

        @Test
        @DisplayName("Should throw when terminating a completed process")
        void shouldThrowWhenTerminatingCompletedProcess() {
            // Arrange
            ProcessDefinition definition = createSimpleLinearProcess();
            engine.deploy(definition);
            ProcessInstance started = engine.startProcess("simple-process", Map.of());

            Token waitingToken = started.getTokens().stream()
                    .filter(t -> t.getState() == TokenState.ACTIVE || t.getState() == TokenState.WAITING)
                    .findFirst()
                    .orElseThrow();
            engine.completeTask(waitingToken.getId(), Map.of());

            // Act & Assert
            assertThatThrownBy(() -> engine.terminateProcess(started.getId()))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Nested
    @DisplayName("getProcessInstance()")
    class GetProcessInstanceTests {

        @Test
        @DisplayName("Should rebuild process instance from events")
        void shouldRebuildFromEvents() {
            // Arrange
            ProcessDefinition definition = createSimpleLinearProcess();
            engine.deploy(definition);
            ProcessInstance started = engine.startProcess("simple-process", Map.of("x", 42));

            // Act
            ProcessInstance rebuilt = engine.getProcessInstance(started.getId());

            // Assert
            assertThat(rebuilt.getId()).isEqualTo(started.getId());
            assertThat(rebuilt.getState()).isEqualTo(ProcessState.RUNNING);
            assertThat(rebuilt.getVariables()).containsEntry("x", 42);
        }

        @Test
        @DisplayName("Should throw when process instance not found")
        void shouldThrowWhenNotFound() {
            // Act & Assert
            assertThatThrownBy(() -> engine.getProcessInstance(UUID.randomUUID()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Process instance not found");
        }
    }

    @Nested
    @DisplayName("failTask()")
    class FailTaskTests {

        @Test
        @DisplayName("Should route token through error boundary when ErrorBoundaryEvent exists")
        void shouldRouteToErrorBoundaryWhenErrorBoundaryExists() {
            // Arrange
            // Process: Start → task1 → End1
            // ErrorBoundaryEvent attached to task1 → errorHandler → End2
            StartEvent startEvent = new StartEvent("start1", "Start", List.of(), List.of("flow1"));
            ServiceTask task1 = new ServiceTask("task1", "Do Work",
                    List.of("flow1"), List.of("flow2"), "work.topic", 0, Duration.ZERO);
            EndEvent endEvent1 = new EndEvent("end1", "End", List.of("flow2"), List.of(), null);

            ErrorBoundaryEvent errorBoundary = new ErrorBoundaryEvent(
                    "errorBoundary1", "Error Boundary",
                    List.of(), List.of("flow-err1"),
                    "task1", "TASK_ERROR", true);
            ServiceTask errorHandler = new ServiceTask("errorHandler", "Handle Error",
                    List.of("flow-err1"), List.of("flow-err2"), "error.topic", 0, Duration.ZERO);
            EndEvent endEvent2 = new EndEvent("end2", "End2", List.of("flow-err2"), List.of(), null);

            SequenceFlow flow1 = new SequenceFlow("flow1", "start1", "task1", null);
            SequenceFlow flow2 = new SequenceFlow("flow2", "task1", "end1", null);
            SequenceFlow flowErr1 = new SequenceFlow("flow-err1", "errorBoundary1", "errorHandler", null);
            SequenceFlow flowErr2 = new SequenceFlow("flow-err2", "errorHandler", "end2", null);

            ProcessDefinition definition = ProcessDefinition.create(
                    "error-boundary-process", 1, "Error Boundary Process", "<xml/>",
                    List.of(startEvent, task1, endEvent1, errorBoundary, errorHandler, endEvent2),
                    List.of(flow1, flow2, flowErr1, flowErr2));
            engine.deploy(definition);

            ProcessInstance started = engine.startProcess("error-boundary-process", Map.of());
            Token waitingToken = started.getTokens().stream()
                    .filter(t -> t.getState() == TokenState.WAITING)
                    .findFirst()
                    .orElseThrow();

            // Act
            ProcessInstance result = engine.failTask(waitingToken.getId(), "TASK_ERROR", "Something went wrong");

            // Assert
            // Token should have been routed through the error boundary to errorHandler (ServiceTask),
            // which sends a message on "error.topic"
            assertThat(result.getState()).isEqualTo(ProcessState.RUNNING);
            assertThat(messageTransport.sendCalls)
                    .anyMatch(call -> call.topic().equals("error.topic"));
        }

        @Test
        @DisplayName("Should trigger compensation and go to ERROR state when no error boundary exists")
        void shouldTriggerCompensationWhenNoErrorBoundary() {
            // Arrange
            // Process: Start → task1 → task2 → End
            // CompensationBoundaryEvent attached to task1 → compensationTask
            StartEvent startEvent = new StartEvent("start1", "Start", List.of(), List.of("flow1"));
            ServiceTask task1 = new ServiceTask("task1", "First Task",
                    List.of("flow1"), List.of("flow2"), "first.topic", 0, Duration.ZERO);
            ServiceTask task2 = new ServiceTask("task2", "Second Task",
                    List.of("flow2"), List.of("flow3"), "second.topic", 0, Duration.ZERO);
            EndEvent endEvent = new EndEvent("end1", "End", List.of("flow3"), List.of(), null);

            CompensationBoundaryEvent compensationBoundary = new CompensationBoundaryEvent(
                    "compBoundary1", "Compensation Boundary",
                    List.of(), List.of("flow-comp1"),
                    "task1");
            ServiceTask compensationTask = new ServiceTask("compTask1", "Undo First Task",
                    List.of("flow-comp1"), List.of(), "compensation.topic", 0, Duration.ZERO);

            SequenceFlow flow1 = new SequenceFlow("flow1", "start1", "task1", null);
            SequenceFlow flow2 = new SequenceFlow("flow2", "task1", "task2", null);
            SequenceFlow flow3 = new SequenceFlow("flow3", "task2", "end1", null);
            SequenceFlow flowComp1 = new SequenceFlow("flow-comp1", "compBoundary1", "compTask1", null);

            ProcessDefinition definition = ProcessDefinition.create(
                    "compensation-process", 1, "Compensation Process", "<xml/>",
                    List.of(startEvent, task1, task2, endEvent, compensationBoundary, compensationTask),
                    List.of(flow1, flow2, flow3, flowComp1));
            engine.deploy(definition);

            // Start process — token arrives at task1 (WAITING)
            ProcessInstance started = engine.startProcess("compensation-process", Map.of());
            Token waitingTask1 = started.getTokens().stream()
                    .filter(t -> t.getState() == TokenState.WAITING)
                    .findFirst()
                    .orElseThrow();

            // Complete task1 — token moves to task2 (WAITING)
            ProcessInstance afterTask1 = engine.completeTask(waitingTask1.getId(), Map.of());
            Token waitingTask2 = afterTask1.getTokens().stream()
                    .filter(t -> t.getState() == TokenState.WAITING)
                    .findFirst()
                    .orElseThrow();

            int sendCallsBefore = messageTransport.sendCalls.size();

            // Act — fail task2 (no error boundary on task2, triggers compensation for task1)
            ProcessInstance result = engine.failTask(waitingTask2.getId(), "FAIL_CODE", "Task 2 failed");

            // Assert — compensation task was sent to MessageTransport
            assertThat(messageTransport.sendCalls.size()).isGreaterThan(sendCallsBefore);
            assertThat(messageTransport.sendCalls)
                    .anyMatch(call -> call.topic().equals("compensation.topic"));

            // Assert — compensation token is registered in instance and WAITING
            assertThat(result.getTokens())
                    .anyMatch(t -> t.getCurrentNodeId().equals("compTask1") && t.getState() == TokenState.WAITING);

            // Assert — process should go to ERROR state (after compensation triggered)
            assertThat(result.getState()).isEqualTo(ProcessState.ERROR);

            // Assert — CompensationTriggeredEvent and ProcessErrorEvent are in event store
            List<ProcessEvent> storedEvents = eventStore.getEvents(result.getId());
            assertThat(storedEvents)
                    .filteredOn(CompensationTriggeredEvent.class::isInstance)
                    .hasSize(1);
            assertThat(storedEvents)
                    .filteredOn(ProcessErrorEvent.class::isInstance)
                    .hasSize(1);
        }

        @Test
        @DisplayName("Should go to ERROR state when no error boundary and no compensation exist")
        void shouldGoToErrorStateWhenNoErrorBoundaryAndNoCompensation() {
            // Arrange
            ProcessDefinition definition = createSimpleLinearProcess();
            engine.deploy(definition);

            ProcessInstance started = engine.startProcess("simple-process", Map.of());
            Token waitingToken = started.getTokens().stream()
                    .filter(t -> t.getState() == TokenState.WAITING)
                    .findFirst()
                    .orElseThrow();

            // Act
            ProcessInstance result = engine.failTask(waitingToken.getId(), "GENERIC_ERROR", "Something failed");

            // Assert
            assertThat(result.getState()).isEqualTo(ProcessState.ERROR);
        }
    }
}
