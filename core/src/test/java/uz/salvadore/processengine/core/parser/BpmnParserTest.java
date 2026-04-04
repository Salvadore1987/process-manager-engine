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
import uz.salvadore.processengine.core.domain.model.SequenceFlow;
import uz.salvadore.processengine.core.domain.model.ServiceTask;
import uz.salvadore.processengine.core.domain.model.StartEvent;
import uz.salvadore.processengine.core.domain.model.TimerBoundaryEvent;
import uz.salvadore.processengine.core.domain.model.TimerDefinition;
import uz.salvadore.processengine.core.domain.model.TimerIntermediateCatchEvent;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BpmnParserTest {

    private final BpmnParser parser = new BpmnParser();

    @Test
    void shouldParseOrderProcessAllFlowNodes() {
        // Arrange
        String bpmnXml = loadBpmn("bpmn/example-order-process.bpmn");

        // Act
        List<ProcessDefinition> definitions = parser.parse(bpmnXml);

        // Assert
        assertThat(definitions).hasSize(1);
        ProcessDefinition definition = definitions.get(0);
        assertThat(definition.getKey()).isEqualTo("order-processing");
        assertThat(definition.getName()).isEqualTo("Order Processing");

        Map<String, FlowNode> nodesById = definition.getFlowNodes().stream()
                .collect(Collectors.toMap(FlowNode::id, Function.identity()));

        assertThat(nodesById.get("StartEvent_OrderReceived")).isInstanceOf(StartEvent.class);
        assertThat(nodesById.get("EndEvent_OrderCompleted")).isInstanceOf(EndEvent.class);
        assertThat(nodesById.get("EndEvent_StockTimeout")).isInstanceOf(EndEvent.class);
        assertThat(nodesById.get("EndEvent_PaymentFailed")).isInstanceOf(EndEvent.class);

        assertThat(nodesById.get("Task_ValidateOrder")).isInstanceOf(ServiceTask.class);
        ServiceTask validateOrder = (ServiceTask) nodesById.get("Task_ValidateOrder");
        assertThat(validateOrder.topic()).isEqualTo("order.validate");

        assertThat(nodesById.get("Task_FraudCheck")).isInstanceOf(ServiceTask.class);
        ServiceTask fraudCheck = (ServiceTask) nodesById.get("Task_FraudCheck");
        assertThat(fraudCheck.topic()).isEqualTo("order.fraud-check");

        assertThat(nodesById.get("Gateway_AmountCheck")).isInstanceOf(ExclusiveGateway.class);
        assertThat(nodesById.get("Gateway_AmountMerge")).isInstanceOf(ExclusiveGateway.class);
        assertThat(nodesById.get("Gateway_ParallelFork")).isInstanceOf(ParallelGateway.class);
        assertThat(nodesById.get("Gateway_ParallelJoin")).isInstanceOf(ParallelGateway.class);

        CallActivity callActivity = (CallActivity) nodesById.get("CallActivity_Payment");
        assertThat(callActivity.calledElement()).isEqualTo("payment-processing");

        TimerBoundaryEvent timerBoundary = (TimerBoundaryEvent) nodesById.get("BoundaryEvent_StockTimeout");
        assertThat(timerBoundary.attachedToRef()).isEqualTo("Task_ReserveStock");
        assertThat(timerBoundary.timerDefinition().type()).isEqualTo(TimerDefinition.TimerType.DURATION);
        assertThat(timerBoundary.timerDefinition().asDuration()).isEqualTo(Duration.ofMinutes(30));
        assertThat(timerBoundary.cancelActivity()).isTrue();

        ErrorBoundaryEvent errorBoundary = (ErrorBoundaryEvent) nodesById.get("BoundaryEvent_PaymentError");
        assertThat(errorBoundary.attachedToRef()).isEqualTo("CallActivity_Payment");
        assertThat(errorBoundary.errorCode()).isEqualTo("PAYMENT_FAILED");

        CompensationBoundaryEvent compBoundary = (CompensationBoundaryEvent) nodesById.get("BoundaryEvent_PaymentCompensation");
        assertThat(compBoundary.attachedToRef()).isEqualTo("CallActivity_Payment");
    }

    @Test
    void shouldParseOrderProcessSequenceFlows() {
        // Arrange
        String bpmnXml = loadBpmn("bpmn/example-order-process.bpmn");

        // Act
        List<ProcessDefinition> definitions = parser.parse(bpmnXml);

        // Assert
        ProcessDefinition definition = definitions.get(0);
        List<SequenceFlow> flows = definition.getSequenceFlows();

        Map<String, SequenceFlow> flowsById = flows.stream()
                .collect(Collectors.toMap(SequenceFlow::id, Function.identity()));

        assertThat(flowsById.get("Flow_ToValidation").sourceRef()).isEqualTo("StartEvent_OrderReceived");
        assertThat(flowsById.get("Flow_ToValidation").targetRef()).isEqualTo("Task_ValidateOrder");

        SequenceFlow highValueFlow = flowsById.get("Flow_HighValue");
        assertThat(highValueFlow.conditionExpression()).isNotNull();
        assertThat(highValueFlow.conditionExpression().expression()).contains("orderAmount");

        SequenceFlow standardFlow = flowsById.get("Flow_StandardValue");
        assertThat(standardFlow.conditionExpression()).isNotNull();
    }

    @Test
    void shouldParsePaymentProcess() {
        // Arrange
        String bpmnXml = loadBpmn("bpmn/example-payment-process.bpmn");

        // Act
        List<ProcessDefinition> definitions = parser.parse(bpmnXml);

        // Assert
        assertThat(definitions).hasSize(1);
        ProcessDefinition definition = definitions.get(0);
        assertThat(definition.getKey()).isEqualTo("payment-processing");
        assertThat(definition.getName()).isEqualTo("Payment Processing");

        assertThat(definition.getFlowNodes()).hasSize(4);
        assertThat(definition.getSequenceFlows()).hasSize(3);

        Map<String, FlowNode> nodesById = definition.getFlowNodes().stream()
                .collect(Collectors.toMap(FlowNode::id, Function.identity()));

        assertThat(nodesById.get("StartEvent_Payment")).isInstanceOf(StartEvent.class);
        assertThat(nodesById.get("EndEvent_PaymentSuccess")).isInstanceOf(EndEvent.class);

        ServiceTask charge = (ServiceTask) nodesById.get("Task_ChargePayment");
        assertThat(charge.topic()).isEqualTo("payment.charge");

        ServiceTask confirm = (ServiceTask) nodesById.get("Task_ConfirmPayment");
        assertThat(confirm.topic()).isEqualTo("payment.confirm");
    }

    @Test
    void shouldRejectBpmnWithUnsupportedElements() {
        // Arrange
        String bpmnXml = loadBpmn("bpmn/invalid-unsupported-elements.bpmn");

        // Act & Assert
        assertThatThrownBy(() -> parser.parse(bpmnXml))
                .isInstanceOf(BpmnParseException.class)
                .hasMessageContaining("unsupported elements");
    }

    @Test
    void shouldParseTimerIntermediateCatchEventWithDuration() {
        // Arrange
        String bpmnXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                                  xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                                  id="Definitions_1" targetNamespace="http://bpmn.io/schema/bpmn">
                  <bpmn:process id="timer-process" name="Timer Process" isExecutable="true">
                    <bpmn:startEvent id="start"><bpmn:outgoing>f1</bpmn:outgoing></bpmn:startEvent>
                    <bpmn:intermediateCatchEvent id="timer1" name="Wait 5s">
                      <bpmn:incoming>f1</bpmn:incoming><bpmn:outgoing>f2</bpmn:outgoing>
                      <bpmn:timerEventDefinition>
                        <bpmn:timeDuration xsi:type="bpmn:tFormalExpression">PT5S</bpmn:timeDuration>
                      </bpmn:timerEventDefinition>
                    </bpmn:intermediateCatchEvent>
                    <bpmn:endEvent id="end"><bpmn:incoming>f2</bpmn:incoming></bpmn:endEvent>
                    <bpmn:sequenceFlow id="f1" sourceRef="start" targetRef="timer1"/>
                    <bpmn:sequenceFlow id="f2" sourceRef="timer1" targetRef="end"/>
                  </bpmn:process>
                </bpmn:definitions>
                """;

        // Act
        List<ProcessDefinition> definitions = parser.parse(bpmnXml);

        // Assert
        ProcessDefinition definition = definitions.getFirst();
        Map<String, FlowNode> nodesById = definition.getFlowNodes().stream()
                .collect(Collectors.toMap(FlowNode::id, Function.identity()));

        assertThat(nodesById.get("timer1")).isInstanceOf(TimerIntermediateCatchEvent.class);
        TimerIntermediateCatchEvent timer = (TimerIntermediateCatchEvent) nodesById.get("timer1");
        assertThat(timer.name()).isEqualTo("Wait 5s");
        assertThat(timer.timerDefinition().type()).isEqualTo(TimerDefinition.TimerType.DURATION);
        assertThat(timer.timerDefinition().value()).isEqualTo("PT5S");
    }

    @Test
    void shouldParseTimerIntermediateCatchEventWithDate() {
        // Arrange
        String bpmnXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                                  xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                                  id="Definitions_1" targetNamespace="http://bpmn.io/schema/bpmn">
                  <bpmn:process id="timer-process" isExecutable="true">
                    <bpmn:startEvent id="start"><bpmn:outgoing>f1</bpmn:outgoing></bpmn:startEvent>
                    <bpmn:intermediateCatchEvent id="timer1">
                      <bpmn:incoming>f1</bpmn:incoming><bpmn:outgoing>f2</bpmn:outgoing>
                      <bpmn:timerEventDefinition>
                        <bpmn:timeDate xsi:type="bpmn:tFormalExpression">2026-04-10T12:00:00Z</bpmn:timeDate>
                      </bpmn:timerEventDefinition>
                    </bpmn:intermediateCatchEvent>
                    <bpmn:endEvent id="end"><bpmn:incoming>f2</bpmn:incoming></bpmn:endEvent>
                    <bpmn:sequenceFlow id="f1" sourceRef="start" targetRef="timer1"/>
                    <bpmn:sequenceFlow id="f2" sourceRef="timer1" targetRef="end"/>
                  </bpmn:process>
                </bpmn:definitions>
                """;

        // Act
        List<ProcessDefinition> definitions = parser.parse(bpmnXml);

        // Assert
        TimerIntermediateCatchEvent timer = (TimerIntermediateCatchEvent) definitions.getFirst()
                .getFlowNodes().stream().filter(n -> n.id().equals("timer1")).findFirst().orElseThrow();
        assertThat(timer.timerDefinition().type()).isEqualTo(TimerDefinition.TimerType.DATE);
        assertThat(timer.timerDefinition().value()).isEqualTo("2026-04-10T12:00:00Z");
    }

    @Test
    void shouldParseTimerIntermediateCatchEventWithCycle() {
        // Arrange
        String bpmnXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                                  xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                                  id="Definitions_1" targetNamespace="http://bpmn.io/schema/bpmn">
                  <bpmn:process id="timer-process" isExecutable="true">
                    <bpmn:startEvent id="start"><bpmn:outgoing>f1</bpmn:outgoing></bpmn:startEvent>
                    <bpmn:intermediateCatchEvent id="timer1">
                      <bpmn:incoming>f1</bpmn:incoming><bpmn:outgoing>f2</bpmn:outgoing>
                      <bpmn:timerEventDefinition>
                        <bpmn:timeCycle xsi:type="bpmn:tFormalExpression">R3/PT10H</bpmn:timeCycle>
                      </bpmn:timerEventDefinition>
                    </bpmn:intermediateCatchEvent>
                    <bpmn:endEvent id="end"><bpmn:incoming>f2</bpmn:incoming></bpmn:endEvent>
                    <bpmn:sequenceFlow id="f1" sourceRef="start" targetRef="timer1"/>
                    <bpmn:sequenceFlow id="f2" sourceRef="timer1" targetRef="end"/>
                  </bpmn:process>
                </bpmn:definitions>
                """;

        // Act
        List<ProcessDefinition> definitions = parser.parse(bpmnXml);

        // Assert
        TimerIntermediateCatchEvent timer = (TimerIntermediateCatchEvent) definitions.getFirst()
                .getFlowNodes().stream().filter(n -> n.id().equals("timer1")).findFirst().orElseThrow();
        assertThat(timer.timerDefinition().type()).isEqualTo(TimerDefinition.TimerType.CYCLE);
        assertThat(timer.timerDefinition().value()).isEqualTo("R3/PT10H");
    }

    @Test
    void shouldParseExclusiveGatewayWithDefaultAttribute() {
        // Arrange
        String bpmnXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                                  xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                                  id="Definitions_1" targetNamespace="http://bpmn.io/schema/bpmn">
                  <bpmn:process id="gw-process" isExecutable="true">
                    <bpmn:startEvent id="start"><bpmn:outgoing>f1</bpmn:outgoing></bpmn:startEvent>
                    <bpmn:exclusiveGateway id="gw1" default="f3">
                      <bpmn:incoming>f1</bpmn:incoming>
                      <bpmn:outgoing>f2</bpmn:outgoing>
                      <bpmn:outgoing>f3</bpmn:outgoing>
                    </bpmn:exclusiveGateway>
                    <bpmn:endEvent id="end1"><bpmn:incoming>f2</bpmn:incoming></bpmn:endEvent>
                    <bpmn:endEvent id="end2"><bpmn:incoming>f3</bpmn:incoming></bpmn:endEvent>
                    <bpmn:sequenceFlow id="f1" sourceRef="start" targetRef="gw1"/>
                    <bpmn:sequenceFlow id="f2" sourceRef="gw1" targetRef="end1">
                      <bpmn:conditionExpression xsi:type="bpmn:tFormalExpression">${x > 10}</bpmn:conditionExpression>
                    </bpmn:sequenceFlow>
                    <bpmn:sequenceFlow id="f3" sourceRef="gw1" targetRef="end2"/>
                  </bpmn:process>
                </bpmn:definitions>
                """;

        // Act
        List<ProcessDefinition> definitions = parser.parse(bpmnXml);

        // Assert
        ExclusiveGateway gateway = (ExclusiveGateway) definitions.getFirst()
                .getFlowNodes().stream().filter(n -> n.id().equals("gw1")).findFirst().orElseThrow();
        assertThat(gateway.defaultFlowId()).isEqualTo("f3");
    }

    @Test
    void shouldParseChargePaymentSubprocess() {
        // Arrange
        String bpmnXml = loadBpmn("bpmn/charge-payment-subprocess.bpmn");

        // Act
        List<ProcessDefinition> definitions = parser.parse(bpmnXml);

        // Assert
        assertThat(definitions).hasSize(1);
        ProcessDefinition definition = definitions.getFirst();
        assertThat(definition.getKey()).isEqualTo("charge-payment-subprocess");

        Map<String, FlowNode> nodesById = definition.getFlowNodes().stream()
                .collect(Collectors.toMap(FlowNode::id, Function.identity()));

        assertThat(nodesById.get("charge-wait")).isInstanceOf(TimerIntermediateCatchEvent.class);
        TimerIntermediateCatchEvent timer = (TimerIntermediateCatchEvent) nodesById.get("charge-wait");
        assertThat(timer.timerDefinition().type()).isEqualTo(TimerDefinition.TimerType.DURATION);
        assertThat(timer.timerDefinition().value()).isEqualTo("PT5S");

        assertThat(nodesById.get("payment-success")).isInstanceOf(ExclusiveGateway.class);
        ExclusiveGateway gateway = (ExclusiveGateway) nodesById.get("payment-success");
        assertThat(gateway.defaultFlowId()).isEqualTo("Flow_18ud5gi");
    }

    @Test
    void shouldThrowOnMalformedXml() {
        // Arrange
        String malformedXml = "<invalid>not valid bpmn</missing>";

        // Act & Assert
        assertThatThrownBy(() -> parser.parse(malformedXml))
                .isInstanceOf(BpmnParseException.class);
    }

    @Test
    void shouldParseErrorEndEvents() {
        // Arrange
        String bpmnXml = loadBpmn("bpmn/example-order-process.bpmn");

        // Act
        List<ProcessDefinition> definitions = parser.parse(bpmnXml);

        // Assert
        ProcessDefinition definition = definitions.get(0);
        Map<String, FlowNode> nodesById = definition.getFlowNodes().stream()
                .collect(Collectors.toMap(FlowNode::id, Function.identity()));

        EndEvent stockTimeoutEnd = (EndEvent) nodesById.get("EndEvent_StockTimeout");
        assertThat(stockTimeoutEnd.errorCode()).isEqualTo("STOCK_TIMEOUT");

        EndEvent paymentFailedEnd = (EndEvent) nodesById.get("EndEvent_PaymentFailed");
        assertThat(paymentFailedEnd.errorCode()).isEqualTo("PAYMENT_FAILED");

        EndEvent normalEnd = (EndEvent) nodesById.get("EndEvent_OrderCompleted");
        assertThat(normalEnd.errorCode()).isNull();
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
