package uz.salvadore.processengine.core.engine.eventsourcing;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import uz.salvadore.processengine.core.domain.enums.ProcessState;
import uz.salvadore.processengine.core.domain.enums.TokenState;
import uz.salvadore.processengine.core.domain.event.CallActivityCompletedEvent;
import uz.salvadore.processengine.core.domain.event.CallActivityStartedEvent;
import uz.salvadore.processengine.core.domain.event.ProcessCompletedEvent;
import uz.salvadore.processengine.core.domain.event.ProcessErrorEvent;
import uz.salvadore.processengine.core.domain.event.ProcessResumedEvent;
import uz.salvadore.processengine.core.domain.event.ProcessStartedEvent;
import uz.salvadore.processengine.core.domain.event.ProcessSuspendedEvent;
import uz.salvadore.processengine.core.domain.event.TaskCompletedEvent;
import uz.salvadore.processengine.core.domain.event.TokenMovedEvent;
import uz.salvadore.processengine.core.domain.model.ProcessInstance;
import uz.salvadore.processengine.core.domain.model.Token;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("EventApplier")
class EventApplierTest {

    private EventApplier eventApplier;

    @BeforeEach
    void setUp() {
        eventApplier = new EventApplier();
    }

    @Nested
    @DisplayName("3.3.1 ProcessStartedEvent")
    class ProcessStartedEventTests {

        @Test
        @DisplayName("should create instance with RUNNING state and initial token at __start__")
        void shouldCreateRunningInstanceWithInitialToken() {
            // Arrange
            UUID processInstanceId = UUID.randomUUID();
            UUID definitionId = UUID.randomUUID();
            Map<String, Object> variables = Map.of("key", "value");
            ProcessStartedEvent event = new ProcessStartedEvent(
                    UUID.randomUUID(), processInstanceId, definitionId, null, variables, Instant.now(), 1L
            );

            // Act
            ProcessInstance result = eventApplier.apply(event, null);

            // Assert
            assertThat(result.getId()).isEqualTo(processInstanceId);
            assertThat(result.getDefinitionId()).isEqualTo(definitionId);
            assertThat(result.getState()).isEqualTo(ProcessState.RUNNING);
            assertThat(result.getTokens()).hasSize(1);
            assertThat(result.getTokens().get(0).getCurrentNodeId()).isEqualTo("__start__");
            assertThat(result.getTokens().get(0).getState()).isEqualTo(TokenState.ACTIVE);
            assertThat(result.getVariables()).containsEntry("key", "value");
            assertThat(result.getStartedAt()).isNotNull();
            assertThat(result.getCompletedAt()).isNull();
        }

        @Test
        @DisplayName("should handle null variables gracefully")
        void shouldHandleNullVariables() {
            // Arrange
            ProcessStartedEvent event = new ProcessStartedEvent(
                    UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null, null, Instant.now(), 1L
            );

            // Act
            ProcessInstance result = eventApplier.apply(event, null);

            // Assert
            assertThat(result.getVariables()).isEmpty();
        }

        @Test
        @DisplayName("should preserve parentProcessInstanceId when provided")
        void shouldPreserveParentProcessInstanceId() {
            // Arrange
            UUID parentId = UUID.randomUUID();
            ProcessStartedEvent event = new ProcessStartedEvent(
                    UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), parentId, Map.of(), Instant.now(), 1L
            );

            // Act
            ProcessInstance result = eventApplier.apply(event, null);

            // Assert
            assertThat(result.getParentProcessInstanceId()).isEqualTo(parentId);
        }
    }

    @Nested
    @DisplayName("3.3.2 TokenMovedEvent")
    class TokenMovedEventTests {

        @Test
        @DisplayName("should update token currentNodeId to toNodeId with ACTIVE state")
        void shouldMoveTokenToNewNode() {
            // Arrange
            ProcessInstance instance = createRunningInstance();
            UUID tokenId = instance.getTokens().get(0).getId();
            TokenMovedEvent event = new TokenMovedEvent(
                    UUID.randomUUID(), instance.getId(), tokenId, "__start__", "serviceTask1", Instant.now(), 2L
            );

            // Act
            ProcessInstance result = eventApplier.apply(event, instance);

            // Assert
            assertThat(result.getTokens()).hasSize(1);
            assertThat(result.getTokens().get(0).getCurrentNodeId()).isEqualTo("serviceTask1");
            assertThat(result.getTokens().get(0).getState()).isEqualTo(TokenState.ACTIVE);
        }

        @Test
        @DisplayName("should not affect other tokens when moving a specific token")
        void shouldNotAffectOtherTokens() {
            // Arrange
            Token token1 = Token.create("node1");
            Token token2 = Token.create("node2");
            ProcessInstance instance = ProcessInstance.restore(
                    UUID.randomUUID(), UUID.randomUUID(), null, ProcessState.RUNNING,
                    List.of(token1, token2), Map.of(), Instant.now(), null
            );
            TokenMovedEvent event = new TokenMovedEvent(
                    UUID.randomUUID(), instance.getId(), token1.getId(), "node1", "node3", Instant.now(), 2L
            );

            // Act
            ProcessInstance result = eventApplier.apply(event, instance);

            // Assert
            assertThat(result.getTokens()).hasSize(2);
            assertThat(result.getTokens().get(0).getCurrentNodeId()).isEqualTo("node3");
            assertThat(result.getTokens().get(1).getCurrentNodeId()).isEqualTo("node2");
        }
    }

    @Nested
    @DisplayName("3.3.3 TaskCompletedEvent")
    class TaskCompletedEventTests {

        @Test
        @DisplayName("should set token state to COMPLETED and merge result variables")
        void shouldCompleteTokenAndMergeVariables() {
            // Arrange
            ProcessInstance instance = createRunningInstance();
            UUID tokenId = instance.getTokens().get(0).getId();
            Map<String, Object> result = Map.of("output", "done");
            TaskCompletedEvent event = new TaskCompletedEvent(
                    UUID.randomUUID(), instance.getId(), tokenId, "__start__", result, Instant.now(), 2L
            );

            // Act
            ProcessInstance resultInstance = eventApplier.apply(event, instance);

            // Assert
            assertThat(resultInstance.getTokens().get(0).getState()).isEqualTo(TokenState.COMPLETED);
            assertThat(resultInstance.getVariables()).containsEntry("output", "done");
        }

        @Test
        @DisplayName("should handle null result map without error")
        void shouldHandleNullResult() {
            // Arrange
            ProcessInstance instance = createRunningInstance();
            UUID tokenId = instance.getTokens().get(0).getId();
            TaskCompletedEvent event = new TaskCompletedEvent(
                    UUID.randomUUID(), instance.getId(), tokenId, "__start__", null, Instant.now(), 2L
            );

            // Act
            ProcessInstance resultInstance = eventApplier.apply(event, instance);

            // Assert
            assertThat(resultInstance.getTokens().get(0).getState()).isEqualTo(TokenState.COMPLETED);
            assertThat(resultInstance.getVariables()).isEqualTo(instance.getVariables());
        }

        @Test
        @DisplayName("should merge result variables with existing variables")
        void shouldMergeWithExistingVariables() {
            // Arrange
            UUID processInstanceId = UUID.randomUUID();
            ProcessStartedEvent startEvent = new ProcessStartedEvent(
                    UUID.randomUUID(), processInstanceId, UUID.randomUUID(), null,
                    Map.of("existing", "value"), Instant.now(), 1L
            );
            ProcessInstance instance = eventApplier.apply(startEvent, null);
            UUID tokenId = instance.getTokens().get(0).getId();
            TaskCompletedEvent event = new TaskCompletedEvent(
                    UUID.randomUUID(), processInstanceId, tokenId, "__start__",
                    Map.of("new", "data"), Instant.now(), 2L
            );

            // Act
            ProcessInstance resultInstance = eventApplier.apply(event, instance);

            // Assert
            assertThat(resultInstance.getVariables())
                    .containsEntry("existing", "value")
                    .containsEntry("new", "data");
        }
    }

    @Nested
    @DisplayName("3.3.4 ProcessSuspendedEvent")
    class ProcessSuspendedEventTests {

        @Test
        @DisplayName("should set state to SUSPENDED")
        void shouldSuspendProcess() {
            // Arrange
            ProcessInstance instance = createRunningInstance();
            ProcessSuspendedEvent event = new ProcessSuspendedEvent(
                    UUID.randomUUID(), instance.getId(), Instant.now(), 2L
            );

            // Act
            ProcessInstance result = eventApplier.apply(event, instance);

            // Assert
            assertThat(result.getState()).isEqualTo(ProcessState.SUSPENDED);
            assertThat(result.getTokens()).isEqualTo(instance.getTokens());
        }
    }

    @Nested
    @DisplayName("3.3.5 ProcessResumedEvent")
    class ProcessResumedEventTests {

        @Test
        @DisplayName("should set state to RUNNING")
        void shouldResumeProcess() {
            // Arrange
            ProcessInstance runningInstance = createRunningInstance();
            ProcessSuspendedEvent suspendEvent = new ProcessSuspendedEvent(
                    UUID.randomUUID(), runningInstance.getId(), Instant.now(), 2L
            );
            ProcessInstance suspendedInstance = eventApplier.apply(suspendEvent, runningInstance);
            ProcessResumedEvent resumeEvent = new ProcessResumedEvent(
                    UUID.randomUUID(), suspendedInstance.getId(), Instant.now(), 3L
            );

            // Act
            ProcessInstance result = eventApplier.apply(resumeEvent, suspendedInstance);

            // Assert
            assertThat(result.getState()).isEqualTo(ProcessState.RUNNING);
        }
    }

    @Nested
    @DisplayName("3.3.6 ProcessCompletedEvent")
    class ProcessCompletedEventTests {

        @Test
        @DisplayName("should set state to COMPLETED")
        void shouldCompleteProcess() {
            // Arrange
            ProcessInstance instance = createRunningInstance();
            ProcessCompletedEvent event = new ProcessCompletedEvent(
                    UUID.randomUUID(), instance.getId(), Instant.now(), 2L
            );

            // Act
            ProcessInstance result = eventApplier.apply(event, instance);

            // Assert
            assertThat(result.getState()).isEqualTo(ProcessState.COMPLETED);
        }
    }

    @Nested
    @DisplayName("3.3.7 ProcessErrorEvent")
    class ProcessErrorEventTests {

        @Test
        @DisplayName("should set state to ERROR")
        void shouldSetErrorState() {
            // Arrange
            ProcessInstance instance = createRunningInstance();
            ProcessErrorEvent event = new ProcessErrorEvent(
                    UUID.randomUUID(), instance.getId(), "ERR_001", "Something failed", Instant.now(), 2L
            );

            // Act
            ProcessInstance result = eventApplier.apply(event, instance);

            // Assert
            assertThat(result.getState()).isEqualTo(ProcessState.ERROR);
        }
    }

    @Nested
    @DisplayName("3.3.8 CallActivity events")
    class CallActivityEventTests {

        @Test
        @DisplayName("CallActivityStartedEvent should set token to WAITING state")
        void shouldSetTokenToWaitingOnCallActivityStarted() {
            // Arrange
            ProcessInstance instance = createRunningInstance();
            UUID tokenId = instance.getTokens().get(0).getId();
            CallActivityStartedEvent event = new CallActivityStartedEvent(
                    UUID.randomUUID(), instance.getId(), tokenId, "callActivity1",
                    UUID.randomUUID(), "childProcess", Instant.now(), 2L
            );

            // Act
            ProcessInstance result = eventApplier.apply(event, instance);

            // Assert
            assertThat(result.getTokens()).hasSize(1);
            assertThat(result.getTokens().get(0).getState()).isEqualTo(TokenState.WAITING);
            assertThat(result.getTokens().get(0).getCurrentNodeId()).isEqualTo("callActivity1");
        }

        @Test
        @DisplayName("CallActivityCompletedEvent should set token to COMPLETED state")
        void shouldSetTokenToCompletedOnCallActivityCompleted() {
            // Arrange
            ProcessInstance instance = createRunningInstance();
            UUID tokenId = instance.getTokens().get(0).getId();
            UUID childProcessId = UUID.randomUUID();

            // First set token to WAITING via CallActivityStarted
            CallActivityStartedEvent startedEvent = new CallActivityStartedEvent(
                    UUID.randomUUID(), instance.getId(), tokenId, "callActivity1",
                    childProcessId, "childProcess", Instant.now(), 2L
            );
            ProcessInstance waitingInstance = eventApplier.apply(startedEvent, instance);

            CallActivityCompletedEvent completedEvent = new CallActivityCompletedEvent(
                    UUID.randomUUID(), instance.getId(), tokenId, "callActivity1",
                    childProcessId, Instant.now(), 3L
            );

            // Act
            ProcessInstance result = eventApplier.apply(completedEvent, waitingInstance);

            // Assert
            assertThat(result.getTokens()).hasSize(1);
            assertThat(result.getTokens().get(0).getState()).isEqualTo(TokenState.COMPLETED);
            assertThat(result.getTokens().get(0).getCurrentNodeId()).isEqualTo("callActivity1");
        }
    }

    /**
     * Helper: creates a RUNNING ProcessInstance via ProcessStartedEvent with one token at "__start__".
     */
    private ProcessInstance createRunningInstance() {
        ProcessStartedEvent event = new ProcessStartedEvent(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null,
                Map.of(), Instant.now(), 1L
        );
        return eventApplier.apply(event, null);
    }
}
