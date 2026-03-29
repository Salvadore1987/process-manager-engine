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
