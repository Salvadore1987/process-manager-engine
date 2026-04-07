package uz.salvadore.processengine.core.parser;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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
import uz.salvadore.processengine.core.domain.model.TimerDefinition;
import uz.salvadore.processengine.core.domain.model.TimerIntermediateCatchEvent;
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

    @Nested
    @DisplayName("Timer Intermediate Catch Event parsing")
    class TimerIntermediateCatchEventTests {

        @Test
        @DisplayName("Should parse intermediateCatchEvent with timeDuration as DURATION")
        void shouldParseTimerDuration() {
            // Arrange
            String bpmnXml = timerBpmn(
                    "<bpmn:timeDuration xsi:type=\"bpmn:tFormalExpression\">PT5S</bpmn:timeDuration>");

            // Act
            List<ProcessDefinition> definitions = parser.parse(bpmnXml);

            // Assert
            ProcessDefinition definition = definitions.get(0);
            Map<String, FlowNode> nodesById = definition.getFlowNodes().stream()
                    .collect(Collectors.toMap(FlowNode::id, Function.identity()));

            assertThat(nodesById.get("timer1")).isInstanceOf(TimerIntermediateCatchEvent.class);
            TimerIntermediateCatchEvent timerNode = (TimerIntermediateCatchEvent) nodesById.get("timer1");
            assertThat(timerNode.type()).isEqualTo(NodeType.TIMER_INTERMEDIATE_CATCH);
            assertThat(timerNode.timerDefinition().type()).isEqualTo(TimerDefinition.TimerType.DURATION);
            assertThat(timerNode.timerDefinition().value()).isEqualTo("PT5S");
        }

        @Test
        @DisplayName("Should parse intermediateCatchEvent with timeDate as DATE")
        void shouldParseTimerDate() {
            // Arrange
            String bpmnXml = timerBpmn(
                    "<bpmn:timeDate xsi:type=\"bpmn:tFormalExpression\">2026-04-10T12:00:00Z</bpmn:timeDate>");

            // Act
            List<ProcessDefinition> definitions = parser.parse(bpmnXml);

            // Assert
            ProcessDefinition definition = definitions.get(0);
            Map<String, FlowNode> nodesById = definition.getFlowNodes().stream()
                    .collect(Collectors.toMap(FlowNode::id, Function.identity()));

            TimerIntermediateCatchEvent timerNode = (TimerIntermediateCatchEvent) nodesById.get("timer1");
            assertThat(timerNode.timerDefinition().type()).isEqualTo(TimerDefinition.TimerType.DATE);
            assertThat(timerNode.timerDefinition().value()).isEqualTo("2026-04-10T12:00:00Z");
        }

        @Test
        @DisplayName("Should parse intermediateCatchEvent with timeCycle as CYCLE")
        void shouldParseTimerCycle() {
            // Arrange
            String bpmnXml = timerBpmn(
                    "<bpmn:timeCycle xsi:type=\"bpmn:tFormalExpression\">R3/PT10H</bpmn:timeCycle>");

            // Act
            List<ProcessDefinition> definitions = parser.parse(bpmnXml);

            // Assert
            ProcessDefinition definition = definitions.get(0);
            Map<String, FlowNode> nodesById = definition.getFlowNodes().stream()
                    .collect(Collectors.toMap(FlowNode::id, Function.identity()));

            TimerIntermediateCatchEvent timerNode = (TimerIntermediateCatchEvent) nodesById.get("timer1");
            assertThat(timerNode.timerDefinition().type()).isEqualTo(TimerDefinition.TimerType.CYCLE);
            assertThat(timerNode.timerDefinition().value()).isEqualTo("R3/PT10H");
        }

        private String timerBpmn(String timerContent) {
            return """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                                      xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                                      id="Definitions_1"
                                      targetNamespace="http://bpmn.io/schema/bpmn">
                      <bpmn:process id="test-timer" isExecutable="true">
                        <bpmn:startEvent id="start"><bpmn:outgoing>f1</bpmn:outgoing></bpmn:startEvent>
                        <bpmn:intermediateCatchEvent id="timer1">
                          <bpmn:incoming>f1</bpmn:incoming><bpmn:outgoing>f2</bpmn:outgoing>
                          <bpmn:timerEventDefinition>
                            %s
                          </bpmn:timerEventDefinition>
                        </bpmn:intermediateCatchEvent>
                        <bpmn:endEvent id="end"><bpmn:incoming>f2</bpmn:incoming></bpmn:endEvent>
                        <bpmn:sequenceFlow id="f1" sourceRef="start" targetRef="timer1"/>
                        <bpmn:sequenceFlow id="f2" sourceRef="timer1" targetRef="end"/>
                      </bpmn:process>
                    </bpmn:definitions>
                    """.formatted(timerContent);
        }
    }

    @Nested
    @DisplayName("ExclusiveGateway default attribute parsing")
    class ExclusiveGatewayDefaultFlowTests {

        @Test
        @DisplayName("Should parse default attribute on exclusiveGateway into defaultFlowId")
        void shouldParseDefaultFlowId() {
            // Arrange
            String bpmnXml = """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                                      xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                                      id="Definitions_1"
                                      targetNamespace="http://bpmn.io/schema/bpmn">
                      <bpmn:process id="test-gw" isExecutable="true">
                        <bpmn:startEvent id="start"><bpmn:outgoing>f_in</bpmn:outgoing></bpmn:startEvent>
                        <bpmn:exclusiveGateway id="gw1" default="flow_default">
                          <bpmn:incoming>f_in</bpmn:incoming>
                          <bpmn:outgoing>flow_cond</bpmn:outgoing>
                          <bpmn:outgoing>flow_default</bpmn:outgoing>
                        </bpmn:exclusiveGateway>
                        <bpmn:serviceTask id="task_cond" name="Cond Task" camunda:type="external" camunda:topic="cond.topic"
                                          xmlns:camunda="http://camunda.org/schema/1.0/bpmn">
                          <bpmn:incoming>flow_cond</bpmn:incoming><bpmn:outgoing>f_end1</bpmn:outgoing>
                        </bpmn:serviceTask>
                        <bpmn:serviceTask id="task_default" name="Default Task" camunda:type="external" camunda:topic="default.topic"
                                          xmlns:camunda="http://camunda.org/schema/1.0/bpmn">
                          <bpmn:incoming>flow_default</bpmn:incoming><bpmn:outgoing>f_end2</bpmn:outgoing>
                        </bpmn:serviceTask>
                        <bpmn:endEvent id="end"><bpmn:incoming>f_end1</bpmn:incoming><bpmn:incoming>f_end2</bpmn:incoming></bpmn:endEvent>
                        <bpmn:sequenceFlow id="f_in" sourceRef="start" targetRef="gw1"/>
                        <bpmn:sequenceFlow id="flow_cond" sourceRef="gw1" targetRef="task_cond">
                          <bpmn:conditionExpression xsi:type="bpmn:tFormalExpression">${amount > 10000}</bpmn:conditionExpression>
                        </bpmn:sequenceFlow>
                        <bpmn:sequenceFlow id="flow_default" sourceRef="gw1" targetRef="task_default"/>
                        <bpmn:sequenceFlow id="f_end1" sourceRef="task_cond" targetRef="end"/>
                        <bpmn:sequenceFlow id="f_end2" sourceRef="task_default" targetRef="end"/>
                      </bpmn:process>
                    </bpmn:definitions>
                    """;

            // Act
            List<ProcessDefinition> definitions = parser.parse(bpmnXml);

            // Assert
            ProcessDefinition definition = definitions.get(0);
            Map<String, FlowNode> nodesById = definition.getFlowNodes().stream()
                    .collect(Collectors.toMap(FlowNode::id, Function.identity()));

            ExclusiveGateway gateway = (ExclusiveGateway) nodesById.get("gw1");
            assertThat(gateway.defaultFlowId()).isEqualTo("flow_default");
        }

        @Test
        @DisplayName("Should parse exclusiveGateway without default attribute as null defaultFlowId")
        void shouldParseNullDefaultFlowId() {
            // Arrange
            String bpmnXml = """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                                      id="Definitions_1"
                                      targetNamespace="http://bpmn.io/schema/bpmn">
                      <bpmn:process id="test-gw" isExecutable="true">
                        <bpmn:startEvent id="start"><bpmn:outgoing>f_in</bpmn:outgoing></bpmn:startEvent>
                        <bpmn:exclusiveGateway id="gw1">
                          <bpmn:incoming>f_in</bpmn:incoming>
                          <bpmn:outgoing>f_out</bpmn:outgoing>
                        </bpmn:exclusiveGateway>
                        <bpmn:endEvent id="end"><bpmn:incoming>f_out</bpmn:incoming></bpmn:endEvent>
                        <bpmn:sequenceFlow id="f_in" sourceRef="start" targetRef="gw1"/>
                        <bpmn:sequenceFlow id="f_out" sourceRef="gw1" targetRef="end"/>
                      </bpmn:process>
                    </bpmn:definitions>
                    """;

            // Act
            List<ProcessDefinition> definitions = parser.parse(bpmnXml);

            // Assert
            ProcessDefinition definition = definitions.get(0);
            Map<String, FlowNode> nodesById = definition.getFlowNodes().stream()
                    .collect(Collectors.toMap(FlowNode::id, Function.identity()));

            ExclusiveGateway gateway = (ExclusiveGateway) nodesById.get("gw1");
            assertThat(gateway.defaultFlowId()).isNull();
        }
    }

    @Nested
    @DisplayName("charge-payment-subprocess.bpmn parsing")
    class ChargePaymentSubprocessParsingTests {

        @Test
        @DisplayName("Should parse charge-payment-subprocess with timer and gateways")
        void shouldParseChargePaymentSubprocess() {
            // Arrange
            String bpmnXml = loadBpmn("bpmn/charge-payment-subprocess.bpmn");

            // Act
            List<ProcessDefinition> definitions = parser.parse(bpmnXml);

            // Assert
            assertThat(definitions).hasSize(1);
            ProcessDefinition definition = definitions.get(0);
            assertThat(definition.getKey()).isEqualTo("charge-payment-subprocess");

            Map<String, FlowNode> nodesById = definition.getFlowNodes().stream()
                    .collect(Collectors.toMap(FlowNode::id, Function.identity()));

            assertThat(nodesById.get("StartEvent_1")).isInstanceOf(StartEvent.class);
            assertThat(nodesById.get("Event_0v6t5nz")).isInstanceOf(EndEvent.class);

            assertThat(nodesById.get("charge-payment")).isInstanceOf(ServiceTask.class);
            ServiceTask chargePayment = (ServiceTask) nodesById.get("charge-payment");
            assertThat(chargePayment.topic()).isEqualTo("order.payment.charge");

            assertThat(nodesById.get("check-status")).isInstanceOf(ServiceTask.class);
            ServiceTask checkStatus = (ServiceTask) nodesById.get("check-status");
            assertThat(checkStatus.topic()).isEqualTo("order.payment.status");

            assertThat(nodesById.get("payment-success")).isInstanceOf(ExclusiveGateway.class);
            ExclusiveGateway paymentSuccess = (ExclusiveGateway) nodesById.get("payment-success");
            assertThat(paymentSuccess.defaultFlowId()).isEqualTo("Flow_18ud5gi");

            assertThat(nodesById.get("payment-status")).isInstanceOf(ExclusiveGateway.class);
            ExclusiveGateway paymentStatus = (ExclusiveGateway) nodesById.get("payment-status");
            assertThat(paymentStatus.defaultFlowId()).isEqualTo("Flow_0a3qg4e");

            assertThat(nodesById.get("charge-wait")).isInstanceOf(TimerIntermediateCatchEvent.class);
            TimerIntermediateCatchEvent timerNode = (TimerIntermediateCatchEvent) nodesById.get("charge-wait");
            assertThat(timerNode.timerDefinition().type()).isEqualTo(TimerDefinition.TimerType.DURATION);
            assertThat(timerNode.timerDefinition().value()).isEqualTo("PT5S");
        }
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
