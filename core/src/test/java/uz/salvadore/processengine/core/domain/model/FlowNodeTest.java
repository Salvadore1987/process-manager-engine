package uz.salvadore.processengine.core.domain.model;

import org.junit.jupiter.api.Test;
import uz.salvadore.processengine.core.domain.enums.NodeType;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FlowNodeTest {

    @Test
    void shouldReturnCorrectTypeForEachFlowNode() {
        // Arrange & Act & Assert
        assertThat(new StartEvent("s", "S", List.of(), List.of()).type()).isEqualTo(NodeType.START_EVENT);
        assertThat(new EndEvent("e", "E", List.of(), List.of(), null).type()).isEqualTo(NodeType.END_EVENT);
        assertThat(new ServiceTask("st", "ST", List.of(), List.of(), "topic", 3, java.time.Duration.ofSeconds(5)).type()).isEqualTo(NodeType.SERVICE_TASK);
        assertThat(new ExclusiveGateway("eg", "EG", List.of(), List.of(), null).type()).isEqualTo(NodeType.EXCLUSIVE_GATEWAY);
        assertThat(new ParallelGateway("pg", "PG", List.of(), List.of()).type()).isEqualTo(NodeType.PARALLEL_GATEWAY);
        assertThat(new CallActivity("ca", "CA", List.of(), List.of(), "sub-process").type()).isEqualTo(NodeType.CALL_ACTIVITY);
        assertThat(new CompensationBoundaryEvent("cb", "CB", List.of(), List.of(), "ref").type()).isEqualTo(NodeType.COMPENSATION_BOUNDARY);
        assertThat(new TimerBoundaryEvent("tb", "TB", List.of(), List.of(), "ref",
                new TimerDefinition(TimerDefinition.TimerType.DURATION, "PT30M"), true).type()).isEqualTo(NodeType.TIMER_BOUNDARY);
        assertThat(new ErrorBoundaryEvent("eb", "EB", List.of(), List.of(), "ref", "ERR_CODE", true).type()).isEqualTo(NodeType.ERROR_BOUNDARY);
    }

    @Test
    void serviceTaskShouldCarryTopic() {
        // Arrange & Act
        ServiceTask task = new ServiceTask("task1", "My Task", List.of("f1"), List.of("f2"),
                "order.validate", 3, java.time.Duration.ofSeconds(10));

        // Assert
        assertThat(task.topic()).isEqualTo("order.validate");
        assertThat(task.retryCount()).isEqualTo(3);
        assertThat(task.retryInterval()).isEqualTo(java.time.Duration.ofSeconds(10));
    }

    @Test
    void callActivityShouldCarryCalledElement() {
        // Arrange & Act
        CallActivity callActivity = new CallActivity("ca1", "Payment", List.of("f1"), List.of("f2"),
                "payment-processing");

        // Assert
        assertThat(callActivity.calledElement()).isEqualTo("payment-processing");
    }

    @Test
    void endEventShouldCarryErrorCode() {
        // Arrange & Act
        EndEvent errorEnd = new EndEvent("end1", "Error End", List.of("f1"), List.of(), "PAYMENT_FAILED");
        EndEvent normalEnd = new EndEvent("end2", "Normal End", List.of("f2"), List.of(), null);

        // Assert
        assertThat(errorEnd.errorCode()).isEqualTo("PAYMENT_FAILED");
        assertThat(normalEnd.errorCode()).isNull();
    }

    @Test
    void boundaryEventsShouldCarryAttachedToRef() {
        // Arrange & Act
        TimerBoundaryEvent timer = new TimerBoundaryEvent("t1", "Timeout", List.of(), List.of("f1"),
                "task1", new TimerDefinition(TimerDefinition.TimerType.DURATION, "PT30M"), true);
        ErrorBoundaryEvent error = new ErrorBoundaryEvent("e1", "Error", List.of(), List.of("f2"),
                "task2", "ERR", true);
        CompensationBoundaryEvent compensation = new CompensationBoundaryEvent("c1", "Comp", List.of(), List.of(),
                "task3");

        // Assert
        assertThat(timer.attachedToRef()).isEqualTo("task1");
        assertThat(timer.timerDefinition().type()).isEqualTo(TimerDefinition.TimerType.DURATION);
        assertThat(timer.timerDefinition().value()).isEqualTo("PT30M");
        assertThat(timer.cancelActivity()).isTrue();
        assertThat(error.attachedToRef()).isEqualTo("task2");
        assertThat(error.errorCode()).isEqualTo("ERR");
        assertThat(compensation.attachedToRef()).isEqualTo("task3");
    }
}
