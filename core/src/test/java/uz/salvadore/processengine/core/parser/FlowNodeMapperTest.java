package uz.salvadore.processengine.core.parser;

import org.junit.jupiter.api.Test;
import uz.salvadore.processengine.core.domain.model.CallActivity;
import uz.salvadore.processengine.core.domain.model.CompensationBoundaryEvent;
import uz.salvadore.processengine.core.domain.model.EndEvent;
import uz.salvadore.processengine.core.domain.model.ErrorBoundaryEvent;
import uz.salvadore.processengine.core.domain.model.ExclusiveGateway;
import uz.salvadore.processengine.core.domain.model.FlowNode;
import uz.salvadore.processengine.core.domain.model.ParallelGateway;
import uz.salvadore.processengine.core.domain.model.ProcessDefinition;
import uz.salvadore.processengine.core.domain.model.ServiceTask;
import uz.salvadore.processengine.core.domain.model.StartEvent;
import uz.salvadore.processengine.core.domain.model.TimerBoundaryEvent;
import uz.salvadore.processengine.core.domain.enums.NodeType;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class FlowNodeMapperTest {

    private final BpmnParser parser = new BpmnParser();

    @Test
    void shouldMapAllFlowNodeTypesCorrectly() {
        // Arrange
        String bpmnXml = loadBpmn("bpmn/example-order-process.bpmn");

        // Act
        List<ProcessDefinition> definitions = parser.parse(bpmnXml);

        // Assert
        ProcessDefinition definition = definitions.get(0);
        Map<String, FlowNode> nodesById = definition.getFlowNodes().stream()
                .collect(Collectors.toMap(FlowNode::id, Function.identity()));

        assertThat(nodesById.get("StartEvent_OrderReceived").type()).isEqualTo(NodeType.START_EVENT);
        assertThat(nodesById.get("EndEvent_OrderCompleted").type()).isEqualTo(NodeType.END_EVENT);
        assertThat(nodesById.get("Task_ValidateOrder").type()).isEqualTo(NodeType.SERVICE_TASK);
        assertThat(nodesById.get("Gateway_AmountCheck").type()).isEqualTo(NodeType.EXCLUSIVE_GATEWAY);
        assertThat(nodesById.get("Gateway_ParallelFork").type()).isEqualTo(NodeType.PARALLEL_GATEWAY);
        assertThat(nodesById.get("CallActivity_Payment").type()).isEqualTo(NodeType.CALL_ACTIVITY);
        assertThat(nodesById.get("BoundaryEvent_StockTimeout").type()).isEqualTo(NodeType.TIMER_BOUNDARY);
        assertThat(nodesById.get("BoundaryEvent_PaymentError").type()).isEqualTo(NodeType.ERROR_BOUNDARY);
        assertThat(nodesById.get("BoundaryEvent_PaymentCompensation").type()).isEqualTo(NodeType.COMPENSATION_BOUNDARY);
    }

    @Test
    void shouldMapServiceTaskTopics() {
        // Arrange
        String bpmnXml = loadBpmn("bpmn/example-order-process.bpmn");

        // Act
        List<ProcessDefinition> definitions = parser.parse(bpmnXml);

        // Assert
        ProcessDefinition definition = definitions.get(0);
        Map<String, FlowNode> nodesById = definition.getFlowNodes().stream()
                .collect(Collectors.toMap(FlowNode::id, Function.identity()));

        assertThat(((ServiceTask) nodesById.get("Task_ValidateOrder")).topic()).isEqualTo("order.validate");
        assertThat(((ServiceTask) nodesById.get("Task_FraudCheck")).topic()).isEqualTo("order.fraud-check");
        assertThat(((ServiceTask) nodesById.get("Task_ReserveStock")).topic()).isEqualTo("warehouse.reserve");
        assertThat(((ServiceTask) nodesById.get("Task_ArrangeShipping")).topic()).isEqualTo("shipping.arrange");
        assertThat(((ServiceTask) nodesById.get("Task_SendConfirmation")).topic()).isEqualTo("notification.order-confirmed");
    }

    @Test
    void shouldMapIncomingAndOutgoingFlows() {
        // Arrange
        String bpmnXml = loadBpmn("bpmn/example-order-process.bpmn");

        // Act
        List<ProcessDefinition> definitions = parser.parse(bpmnXml);

        // Assert
        ProcessDefinition definition = definitions.get(0);
        Map<String, FlowNode> nodesById = definition.getFlowNodes().stream()
                .collect(Collectors.toMap(FlowNode::id, Function.identity()));

        StartEvent start = (StartEvent) nodesById.get("StartEvent_OrderReceived");
        assertThat(start.incomingFlows()).isEmpty();
        assertThat(start.outgoingFlows()).containsExactly("Flow_ToValidation");

        ExclusiveGateway amountCheck = (ExclusiveGateway) nodesById.get("Gateway_AmountCheck");
        assertThat(amountCheck.incomingFlows()).containsExactly("Flow_ToAmountCheck");
        assertThat(amountCheck.outgoingFlows()).containsExactlyInAnyOrder("Flow_HighValue", "Flow_StandardValue");

        ParallelGateway fork = (ParallelGateway) nodesById.get("Gateway_ParallelFork");
        assertThat(fork.outgoingFlows()).hasSize(2);

        ParallelGateway join = (ParallelGateway) nodesById.get("Gateway_ParallelJoin");
        assertThat(join.incomingFlows()).hasSize(2);
    }

    private String loadBpmn(String resourcePath) {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IllegalArgumentException("Resource not found: " + resourcePath);
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load BPMN resource: " + resourcePath, e);
        }
    }
}
