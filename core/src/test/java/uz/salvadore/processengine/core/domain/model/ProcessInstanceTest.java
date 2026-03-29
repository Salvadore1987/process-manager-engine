package uz.salvadore.processengine.core.domain.model;

import org.junit.jupiter.api.Test;
import uz.salvadore.processengine.core.domain.enums.ProcessState;
import uz.salvadore.processengine.core.util.UUIDv7;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProcessInstanceTest {

    @Test
    void shouldCreateRunningInstance() {
        // Arrange
        java.util.UUID defId = UUIDv7.generate();

        // Act
        ProcessInstance instance = ProcessInstance.create(defId, Map.of("key", "value"));

        // Assert
        assertThat(instance.getId()).isNotNull();
        assertThat(instance.getDefinitionId()).isEqualTo(defId);
        assertThat(instance.getState()).isEqualTo(ProcessState.RUNNING);
        assertThat(instance.getParentProcessInstanceId()).isNull();
        assertThat(instance.getVariables()).containsEntry("key", "value");
        assertThat(instance.getStartedAt()).isNotNull();
        assertThat(instance.getCompletedAt()).isNull();
    }

    @Test
    void shouldCreateChildInstance() {
        // Arrange
        java.util.UUID defId = UUIDv7.generate();
        java.util.UUID parentId = UUIDv7.generate();

        // Act
        ProcessInstance child = ProcessInstance.createChild(defId, parentId, Map.of());

        // Assert
        assertThat(child.getParentProcessInstanceId()).isEqualTo(parentId);
        assertThat(child.getState()).isEqualTo(ProcessState.RUNNING);
    }

    @Test
    void shouldSuspendRunningInstance() {
        // Arrange
        ProcessInstance instance = ProcessInstance.create(UUIDv7.generate(), Map.of());

        // Act
        ProcessInstance suspended = instance.suspend();

        // Assert
        assertThat(suspended.getState()).isEqualTo(ProcessState.SUSPENDED);
    }

    @Test
    void shouldResumeAfterSuspend() {
        // Arrange
        ProcessInstance instance = ProcessInstance.create(UUIDv7.generate(), Map.of()).suspend();

        // Act
        ProcessInstance resumed = instance.resume();

        // Assert
        assertThat(resumed.getState()).isEqualTo(ProcessState.RUNNING);
    }

    @Test
    void shouldCompleteRunningInstance() {
        // Arrange
        ProcessInstance instance = ProcessInstance.create(UUIDv7.generate(), Map.of());

        // Act
        ProcessInstance completed = instance.complete();

        // Assert
        assertThat(completed.getState()).isEqualTo(ProcessState.COMPLETED);
        assertThat(completed.getCompletedAt()).isNotNull();
    }

    @Test
    void shouldSetErrorOnRunningInstance() {
        // Arrange
        ProcessInstance instance = ProcessInstance.create(UUIDv7.generate(), Map.of());

        // Act
        ProcessInstance errored = instance.error();

        // Assert
        assertThat(errored.getState()).isEqualTo(ProcessState.ERROR);
        assertThat(errored.getCompletedAt()).isNotNull();
    }

    @Test
    void shouldTerminateRunningInstance() {
        // Arrange
        ProcessInstance instance = ProcessInstance.create(UUIDv7.generate(), Map.of());

        // Act
        ProcessInstance terminated = instance.terminate();

        // Assert
        assertThat(terminated.getState()).isEqualTo(ProcessState.TERMINATED);
    }

    @Test
    void shouldTerminateSuspendedInstance() {
        // Arrange
        ProcessInstance instance = ProcessInstance.create(UUIDv7.generate(), Map.of()).suspend();

        // Act
        ProcessInstance terminated = instance.terminate();

        // Assert
        assertThat(terminated.getState()).isEqualTo(ProcessState.TERMINATED);
    }

    @Test
    void shouldNotSuspendCompletedInstance() {
        // Arrange
        ProcessInstance instance = ProcessInstance.create(UUIDv7.generate(), Map.of()).complete();

        // Act & Assert
        assertThatThrownBy(instance::suspend)
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldNotResumeRunningInstance() {
        // Arrange
        ProcessInstance instance = ProcessInstance.create(UUIDv7.generate(), Map.of());

        // Act & Assert
        assertThatThrownBy(instance::resume)
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldNotTerminateCompletedInstance() {
        // Arrange
        ProcessInstance instance = ProcessInstance.create(UUIDv7.generate(), Map.of()).complete();

        // Act & Assert
        assertThatThrownBy(instance::terminate)
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldNotCompleteErroredInstance() {
        // Arrange
        ProcessInstance instance = ProcessInstance.create(UUIDv7.generate(), Map.of()).error();

        // Act & Assert
        assertThatThrownBy(instance::complete)
                .isInstanceOf(IllegalStateException.class);
    }
}
