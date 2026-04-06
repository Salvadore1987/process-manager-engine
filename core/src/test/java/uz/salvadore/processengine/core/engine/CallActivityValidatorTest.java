package uz.salvadore.processengine.core.engine;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import uz.salvadore.processengine.core.domain.exception.CallActivitySubprocessNotFoundException;
import uz.salvadore.processengine.core.domain.model.DeploymentBundle;
import uz.salvadore.processengine.core.domain.model.ProcessDefinition;
import uz.salvadore.processengine.core.parser.BpmnParser;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CallActivityValidatorTest {

    private BpmnParser bpmnParser;
    private CallActivityValidator validator;

    @BeforeEach
    void setUp() {
        bpmnParser = new BpmnParser();
        validator = new CallActivityValidator(bpmnParser);
    }

    /**
     * Builds a minimal valid BPMN 2.0 XML with a single process.
     * If {@code calledElement} is non-null, a callActivity element is inserted.
     */
    private static String minimalBpmn(String processId, String calledElement) {
        String callActivityBlock = "";
        String extraFlow = "";
        String extraSequenceFlow = "";

        if (calledElement != null) {
            callActivityBlock = """
                    <bpmn:callActivity id="call1" name="Call Sub" calledElement="%s">
                      <bpmn:incoming>flow1</bpmn:incoming>
                      <bpmn:outgoing>flow2</bpmn:outgoing>
                    </bpmn:callActivity>
                """.formatted(calledElement);

            extraFlow = """
                    <bpmn:sequenceFlow id="flow1" sourceRef="start1" targetRef="call1" />
                    <bpmn:sequenceFlow id="flow2" sourceRef="call1" targetRef="end1" />
                """;
        } else {
            extraFlow = """
                    <bpmn:sequenceFlow id="flow1" sourceRef="start1" targetRef="end1" />
                """;
        }

        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                                  id="Definitions_1"
                                  targetNamespace="http://bpmn.io/schema/bpmn">
                  <bpmn:process id="%s" isExecutable="true">
                    <bpmn:startEvent id="start1">
                      <bpmn:outgoing>flow1</bpmn:outgoing>
                    </bpmn:startEvent>
                    %s
                    <bpmn:endEvent id="end1">
                      <bpmn:incoming>%s</bpmn:incoming>
                    </bpmn:endEvent>
                    %s
                  </bpmn:process>
                </bpmn:definitions>
                """.formatted(
                processId,
                callActivityBlock,
                calledElement != null ? "flow2" : "flow1",
                extraFlow
        );
    }

    @Nested
    @DisplayName("validate()")
    class ValidateTests {

        @Test
        @DisplayName("Should pass when all subprocesses are present in the bundle")
        void shouldPassWhenAllSubprocessesFoundInBundle() {
            // Arrange
            String mainXml = minimalBpmn("main-process", "sub-process");
            String subXml = minimalBpmn("sub-process", null);

            Map<String, String> files = new LinkedHashMap<>();
            files.put("main-process.bpmn", mainXml);
            files.put("sub-process.bpmn", subXml);
            DeploymentBundle bundle = new DeploymentBundle(files);

            ProcessDefinition mainDefinition = bpmnParser.parse(mainXml).getFirst();

            // Act & Assert
            assertThatCode(() -> validator.validate(mainDefinition, bundle))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Should throw CallActivitySubprocessNotFoundException when subprocess is missing from bundle")
        void shouldThrowWhenSubprocessMissingFromBundle() {
            // Arrange
            String mainXml = minimalBpmn("main-process", "missing-sub");

            Map<String, String> files = new LinkedHashMap<>();
            files.put("main-process.bpmn", mainXml);
            DeploymentBundle bundle = new DeploymentBundle(files);

            ProcessDefinition mainDefinition = bpmnParser.parse(mainXml).getFirst();

            // Act & Assert
            assertThatThrownBy(() -> validator.validate(mainDefinition, bundle))
                    .isInstanceOf(CallActivitySubprocessNotFoundException.class)
                    .hasMessageContaining("missing-sub");
        }

        @Test
        @DisplayName("Should validate nested subprocesses recursively (A calls B calls C)")
        void shouldValidateNestedSubprocessesRecursively() {
            // Arrange
            String xmlA = minimalBpmn("process-a", "process-b");
            String xmlB = minimalBpmn("process-b", "process-c");
            String xmlC = minimalBpmn("process-c", null);

            Map<String, String> files = new LinkedHashMap<>();
            files.put("process-a.bpmn", xmlA);
            files.put("process-b.bpmn", xmlB);
            files.put("process-c.bpmn", xmlC);
            DeploymentBundle bundle = new DeploymentBundle(files);

            ProcessDefinition definitionA = bpmnParser.parse(xmlA).getFirst();

            // Act & Assert
            assertThatCode(() -> validator.validate(definitionA, bundle))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Should throw IllegalStateException when cyclic dependency is detected (A calls B calls A)")
        void shouldThrowWhenCyclicDependencyDetected() {
            // Arrange
            String xmlA = minimalBpmn("process-a", "process-b");
            String xmlB = minimalBpmn("process-b", "process-a");

            Map<String, String> files = new LinkedHashMap<>();
            files.put("process-a.bpmn", xmlA);
            files.put("process-b.bpmn", xmlB);
            DeploymentBundle bundle = new DeploymentBundle(files);

            ProcessDefinition definitionA = bpmnParser.parse(xmlA).getFirst();

            // Act & Assert
            assertThatThrownBy(() -> validator.validate(definitionA, bundle))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Cyclic Call Activity dependency detected");
        }
    }
}
