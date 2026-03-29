package uz.salvadore.processengine.rest.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import uz.salvadore.processengine.core.domain.model.EndEvent;
import uz.salvadore.processengine.core.domain.model.ProcessDefinition;
import uz.salvadore.processengine.core.domain.model.SequenceFlow;
import uz.salvadore.processengine.core.domain.model.StartEvent;
import uz.salvadore.processengine.core.engine.ProcessDefinitionRepository;
import uz.salvadore.processengine.core.engine.ProcessEngine;
import uz.salvadore.processengine.core.parser.BpmnParser;
import uz.salvadore.processengine.core.parser.BpmnValidationResult;
import uz.salvadore.processengine.core.parser.UnsupportedElementError;
import uz.salvadore.processengine.core.port.outgoing.ProcessEventStore;
import uz.salvadore.processengine.rest.mapper.ProcessDefinitionDtoMapper;
import uz.salvadore.processengine.rest.mapper.ProcessInstanceDtoMapper;
import uz.salvadore.processengine.rest.mapper.ProcessEventDtoMapper;
import uz.salvadore.processengine.rest.config.NoSecurityTestConfig;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProcessDefinitionController.class)
@Import({ProcessDefinitionDtoMapper.class, ProcessInstanceDtoMapper.class, ProcessEventDtoMapper.class, NoSecurityTestConfig.class})
class ProcessDefinitionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ProcessEngine processEngine;

    @MockBean
    private ProcessDefinitionRepository definitionRepository;

    @MockBean
    private BpmnParser bpmnParser;

    @MockBean
    private ProcessEventStore processEventStore;

    private ProcessDefinition createTestDefinition() {
        return ProcessDefinition.create(
                "order-processing", 1, "Order Processing", "<bpmn/>",
                List.of(
                        new StartEvent("start", "Start", List.of(), List.of("flow1")),
                        new EndEvent("end", "End", List.of("flow1"), List.of(), null)
                ),
                List.of(new SequenceFlow("flow1", "start", "end", null))
        );
    }

    @Test
    void shouldDeployValidBpmnAndReturn201() throws Exception {
        // Arrange
        ProcessDefinition definition = createTestDefinition();
        String bpmnContent = "<bpmn>valid</bpmn>";
        MockMultipartFile file = new MockMultipartFile(
                "file", "process.bpmn", "application/xml",
                bpmnContent.getBytes(StandardCharsets.UTF_8));

        when(bpmnParser.validate(anyString())).thenReturn(BpmnValidationResult.success());
        when(bpmnParser.parse(anyString())).thenReturn(List.of(definition));
        doNothing().when(processEngine).deploy(any(ProcessDefinition.class));

        // Act & Assert
        mockMvc.perform(multipart("/api/v1/definitions").file(file))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(definition.getId().toString()))
                .andExpect(jsonPath("$.key").value("order-processing"))
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.name").value("Order Processing"))
                .andExpect(jsonPath("$.deployedAt").value(notNullValue()));

        verify(processEngine).deploy(any(ProcessDefinition.class));
    }

    @Test
    void shouldReturn400WhenBpmnValidationFails() throws Exception {
        // Arrange
        String bpmnContent = "<bpmn>invalid</bpmn>";
        MockMultipartFile file = new MockMultipartFile(
                "file", "process.bpmn", "application/xml",
                bpmnContent.getBytes(StandardCharsets.UTF_8));

        List<UnsupportedElementError> errors = List.of(
                new UnsupportedElementError("bpmn:SubProcess", "sub1", "MySubProcess", 10)
        );
        BpmnValidationResult failedResult = BpmnValidationResult.failure(errors);
        when(bpmnParser.validate(anyString())).thenReturn(failedResult);

        // Act & Assert
        mockMvc.perform(multipart("/api/v1/definitions").file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.unsupportedElements", hasSize(1)))
                .andExpect(jsonPath("$.unsupportedElements[0].element").value("bpmn:SubProcess"))
                .andExpect(jsonPath("$.unsupportedElements[0].id").value("sub1"))
                .andExpect(jsonPath("$.unsupportedElements[0].name").value("MySubProcess"))
                .andExpect(jsonPath("$.unsupportedElements[0].line").value(10));
    }

    @Test
    void shouldValidateBpmnAndReturn200WithValidResult() throws Exception {
        // Arrange
        String bpmnContent = "<bpmn>valid</bpmn>";
        MockMultipartFile file = new MockMultipartFile(
                "file", "process.bpmn", "application/xml",
                bpmnContent.getBytes(StandardCharsets.UTF_8));

        when(bpmnParser.validate(anyString())).thenReturn(BpmnValidationResult.success());

        // Act & Assert
        mockMvc.perform(multipart("/api/v1/definitions/validate").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.unsupportedElements", hasSize(0)));
    }

    @Test
    void shouldListAllDefinitionsAndReturn200() throws Exception {
        // Arrange
        ProcessDefinition definition = createTestDefinition();
        when(definitionRepository.list()).thenReturn(List.of(definition));

        // Act & Assert
        mockMvc.perform(get("/api/v1/definitions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].key").value("order-processing"))
                .andExpect(jsonPath("$[0].version").value(1))
                .andExpect(jsonPath("$[0].name").value("Order Processing"));
    }

    @Test
    void shouldGetDefinitionByKeyAndReturn200() throws Exception {
        // Arrange
        ProcessDefinition definition = createTestDefinition();
        when(definitionRepository.getByKey("order-processing")).thenReturn(Optional.of(definition));

        // Act & Assert
        mockMvc.perform(get("/api/v1/definitions/order-processing"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(definition.getId().toString()))
                .andExpect(jsonPath("$.key").value("order-processing"))
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.name").value("Order Processing"));
    }

    @Test
    void shouldReturn404WhenDefinitionNotFoundByKey() throws Exception {
        // Arrange
        when(definitionRepository.getByKey("non-existent")).thenReturn(Optional.empty());

        // Act & Assert
        mockMvc.perform(get("/api/v1/definitions/non-existent"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Process definition not found: non-existent"));
    }

    @Test
    void shouldGetVersionsByKeyAndReturn200() throws Exception {
        // Arrange
        ProcessDefinition definition = createTestDefinition();
        when(definitionRepository.getVersions("order-processing")).thenReturn(List.of(definition));

        // Act & Assert
        mockMvc.perform(get("/api/v1/definitions/order-processing/versions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].key").value("order-processing"));
    }

    @Test
    void shouldUndeployDefinitionAndReturn204() throws Exception {
        // Arrange
        doNothing().when(definitionRepository).undeploy("order-processing");

        // Act & Assert
        mockMvc.perform(delete("/api/v1/definitions/order-processing"))
                .andExpect(status().isNoContent());

        verify(definitionRepository).undeploy("order-processing");
    }
}
