package uz.salvadore.processengine.core.parser;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class BpmnValidatorTest {

    private final BpmnValidator validator = new BpmnValidator();

    @Test
    void shouldPassValidBpmn() {
        // Arrange
        String bpmnXml = loadBpmn("bpmn/example-order-process.bpmn");

        // Act
        BpmnValidationResult result = validator.validate(bpmnXml);

        // Assert
        assertThat(result.valid()).isTrue();
        assertThat(result.unsupportedElements()).isEmpty();
    }

    @Test
    void shouldPassPaymentProcessBpmn() {
        // Arrange
        String bpmnXml = loadBpmn("bpmn/example-payment-process.bpmn");

        // Act
        BpmnValidationResult result = validator.validate(bpmnXml);

        // Assert
        assertThat(result.valid()).isTrue();
    }

    @Test
    void shouldDetectUnsupportedElements() {
        // Arrange
        String bpmnXml = loadBpmn("bpmn/invalid-unsupported-elements.bpmn");

        // Act
        BpmnValidationResult result = validator.validate(bpmnXml);

        // Assert
        assertThat(result.valid()).isFalse();
        assertThat(result.unsupportedElements()).hasSize(2);

        assertThat(result.unsupportedElements())
                .extracting(UnsupportedElementError::element)
                .containsExactlyInAnyOrder("bpmn:userTask", "bpmn:scriptTask");

        assertThat(result.unsupportedElements())
                .extracting(UnsupportedElementError::id)
                .containsExactlyInAnyOrder("task1", "task2");

        assertThat(result.unsupportedElements())
                .extracting(UnsupportedElementError::name)
                .containsExactlyInAnyOrder("Approve Request", "Calculate Total");

        assertThat(result.unsupportedElements())
                .allSatisfy(error -> assertThat(error.line()).isGreaterThan(0));
    }

    @Test
    void shouldPassBpmnWithTimerIntermediateCatchEvent() {
        // Arrange
        String bpmnXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                                  xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                                  id="Definitions_1"
                                  targetNamespace="http://bpmn.io/schema/bpmn">
                  <bpmn:process id="test-process" isExecutable="true">
                    <bpmn:startEvent id="start"><bpmn:outgoing>f1</bpmn:outgoing></bpmn:startEvent>
                    <bpmn:intermediateCatchEvent id="timer1">
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
        BpmnValidationResult result = validator.validate(bpmnXml);

        // Assert
        assertThat(result.valid()).isTrue();
        assertThat(result.unsupportedElements()).isEmpty();
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
