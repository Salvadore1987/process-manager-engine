package uz.salvadore.processengine.core.parser;

import org.junit.jupiter.api.Test;
import uz.salvadore.processengine.core.domain.model.FlowNode;
import uz.salvadore.processengine.core.domain.model.ProcessDefinition;
import uz.salvadore.processengine.core.domain.model.SequenceFlow;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class BpmnParserRoundtripTest {

    private final BpmnParser parser = new BpmnParser();

    @Test
    void shouldParseOrderProcessGraphStructure() {
        // Arrange
        String bpmnXml = loadBpmn("bpmn/example-order-process.bpmn");

        // Act
        List<ProcessDefinition> definitions = parser.parse(bpmnXml);

        // Assert
        ProcessDefinition definition = definitions.get(0);

        // 17 flow nodes total in order process:
        // 1 StartEvent + 3 EndEvents + 8 ServiceTasks (incl. RefundPayment, NotifyStockTimeout, NotifyPaymentFailure)
        // + 2 ExclusiveGateways + 2 ParallelGateways + 1 CallActivity
        // + 1 TimerBoundary + 1 ErrorBoundary + 1 CompensationBoundary = 20? Let's count:
        // StartEvent_OrderReceived, Task_ValidateOrder, Gateway_AmountCheck, Task_FraudCheck,
        // Gateway_AmountMerge, Gateway_ParallelFork, Task_ReserveStock, BoundaryEvent_StockTimeout,
        // Task_NotifyStockTimeout, EndEvent_StockTimeout, CallActivity_Payment, BoundaryEvent_PaymentError,
        // Task_NotifyPaymentFailure, EndEvent_PaymentFailed, BoundaryEvent_PaymentCompensation,
        // Task_RefundPayment, Gateway_ParallelJoin, Task_ArrangeShipping, Task_SendConfirmation,
        // EndEvent_OrderCompleted = 20 nodes
        assertThat(definition.getFlowNodes()).hasSize(20);

        // Verify graph connectivity via sequence flows
        Map<String, FlowNode> nodesById = definition.getFlowNodes().stream()
                .collect(Collectors.toMap(FlowNode::id, Function.identity()));
        Map<String, SequenceFlow> flowsById = definition.getSequenceFlows().stream()
                .collect(Collectors.toMap(SequenceFlow::id, Function.identity()));

        // Verify all sequence flow references point to existing nodes
        for (SequenceFlow flow : definition.getSequenceFlows()) {
            assertThat(nodesById).containsKey(flow.sourceRef());
            assertThat(nodesById).containsKey(flow.targetRef());
        }

        // Verify main path: Start → Validate → AmountCheck → ... → End
        assertThat(flowsById.get("Flow_ToValidation").sourceRef()).isEqualTo("StartEvent_OrderReceived");
        assertThat(flowsById.get("Flow_ToValidation").targetRef()).isEqualTo("Task_ValidateOrder");
        assertThat(flowsById.get("Flow_ToEnd").targetRef()).isEqualTo("EndEvent_OrderCompleted");

        // Verify parallel fork has 2 outgoing
        Set<String> forkTargets = definition.getSequenceFlows().stream()
                .filter(f -> f.sourceRef().equals("Gateway_ParallelFork"))
                .map(SequenceFlow::targetRef)
                .collect(Collectors.toSet());
        assertThat(forkTargets).containsExactlyInAnyOrder("Task_ReserveStock", "CallActivity_Payment");

        // Verify parallel join has 2 incoming
        Set<String> joinSources = definition.getSequenceFlows().stream()
                .filter(f -> f.targetRef().equals("Gateway_ParallelJoin"))
                .map(SequenceFlow::sourceRef)
                .collect(Collectors.toSet());
        assertThat(joinSources).containsExactlyInAnyOrder("Task_ReserveStock", "CallActivity_Payment");
    }

    @Test
    void shouldParsePaymentProcessGraphStructure() {
        // Arrange
        String bpmnXml = loadBpmn("bpmn/example-payment-process.bpmn");

        // Act
        List<ProcessDefinition> definitions = parser.parse(bpmnXml);

        // Assert
        ProcessDefinition definition = definitions.get(0);
        assertThat(definition.getFlowNodes()).hasSize(4);
        assertThat(definition.getSequenceFlows()).hasSize(3);

        // Verify linear flow: Start → Charge → Confirm → End
        Map<String, SequenceFlow> flowsById = definition.getSequenceFlows().stream()
                .collect(Collectors.toMap(SequenceFlow::id, Function.identity()));

        assertThat(flowsById.get("Flow_P_ToCharge").sourceRef()).isEqualTo("StartEvent_Payment");
        assertThat(flowsById.get("Flow_P_ToCharge").targetRef()).isEqualTo("Task_ChargePayment");
        assertThat(flowsById.get("Flow_P_ToConfirm").sourceRef()).isEqualTo("Task_ChargePayment");
        assertThat(flowsById.get("Flow_P_ToConfirm").targetRef()).isEqualTo("Task_ConfirmPayment");
        assertThat(flowsById.get("Flow_P_ToEnd").sourceRef()).isEqualTo("Task_ConfirmPayment");
        assertThat(flowsById.get("Flow_P_ToEnd").targetRef()).isEqualTo("EndEvent_PaymentSuccess");
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
