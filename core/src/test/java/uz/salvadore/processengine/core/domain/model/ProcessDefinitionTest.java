package uz.salvadore.processengine.core.domain.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProcessDefinitionTest {

    @Test
    void shouldCreateValidProcessDefinition() {
        // Arrange
        StartEvent start = new StartEvent("start1", "Start", List.of(), List.of("flow1"));
        EndEvent end = new EndEvent("end1", "End", List.of("flow1"), List.of(), null);
        SequenceFlow flow = new SequenceFlow("flow1", "start1", "end1", null);

        // Act
        ProcessDefinition definition = ProcessDefinition.create(
                "test-process", 1, "Test Process", "<xml/>",
                List.of(start, end), List.of(flow));

        // Assert
        assertThat(definition.getId()).isNotNull();
        assertThat(definition.getKey()).isEqualTo("test-process");
        assertThat(definition.getVersion()).isEqualTo(1);
        assertThat(definition.getName()).isEqualTo("Test Process");
        assertThat(definition.getFlowNodes()).hasSize(2);
        assertThat(definition.getSequenceFlows()).hasSize(1);
        assertThat(definition.getDeployedAt()).isNotNull();
    }

    @Test
    void shouldRejectDefinitionWithoutStartEvent() {
        // Arrange
        EndEvent end = new EndEvent("end1", "End", List.of(), List.of(), null);

        // Act & Assert
        assertThatThrownBy(() -> ProcessDefinition.create(
                "test", 1, "Test", "<xml/>", List.of(end), List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("StartEvent");
    }

    @Test
    void shouldRejectDefinitionWithMultipleStartEvents() {
        // Arrange
        StartEvent start1 = new StartEvent("start1", "Start 1", List.of(), List.of("flow1"));
        StartEvent start2 = new StartEvent("start2", "Start 2", List.of(), List.of("flow2"));
        EndEvent end = new EndEvent("end1", "End", List.of("flow1", "flow2"), List.of(), null);

        // Act & Assert
        assertThatThrownBy(() -> ProcessDefinition.create(
                "test", 1, "Test", "<xml/>", List.of(start1, start2, end), List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("StartEvent");
    }

    @Test
    void shouldRejectDefinitionWithoutEndEvent() {
        // Arrange
        StartEvent start = new StartEvent("start1", "Start", List.of(), List.of());

        // Act & Assert
        assertThatThrownBy(() -> ProcessDefinition.create(
                "test", 1, "Test", "<xml/>", List.of(start), List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("EndEvent");
    }

    @Test
    void shouldReturnImmutableFlowNodesList() {
        // Arrange
        StartEvent start = new StartEvent("start1", "Start", List.of(), List.of("flow1"));
        EndEvent end = new EndEvent("end1", "End", List.of("flow1"), List.of(), null);
        ProcessDefinition definition = ProcessDefinition.create(
                "test", 1, "Test", "<xml/>", List.of(start, end), List.of());

        // Act & Assert
        assertThatThrownBy(() -> definition.getFlowNodes().add(
                new StartEvent("s2", "S2", List.of(), List.of())))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
