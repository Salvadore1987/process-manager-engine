package uz.salvadore.processengine.rest.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import uz.salvadore.processengine.core.domain.model.EndEvent;
import uz.salvadore.processengine.core.domain.model.ProcessDefinition;
import uz.salvadore.processengine.core.domain.model.SequenceFlow;
import uz.salvadore.processengine.core.domain.model.StartEvent;
import uz.salvadore.processengine.core.engine.ProcessDefinitionRepository;
import uz.salvadore.processengine.core.engine.ProcessEngine;
import uz.salvadore.processengine.core.parser.BpmnParser;
import uz.salvadore.processengine.core.parser.BpmnValidationResult;
import uz.salvadore.processengine.core.port.outgoing.ProcessEventStore;
import uz.salvadore.processengine.rest.config.SecurityEnabledTestConfig;
import uz.salvadore.processengine.rest.mapper.ProcessDefinitionDtoMapper;
import uz.salvadore.processengine.rest.mapper.ProcessEventDtoMapper;
import uz.salvadore.processengine.rest.mapper.ProcessInstanceDtoMapper;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProcessDefinitionController.class)
@Import({ProcessDefinitionDtoMapper.class, ProcessInstanceDtoMapper.class, ProcessEventDtoMapper.class, SecurityEnabledTestConfig.class})
class DefinitionControllerSecurityTest {

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

    private MockMultipartFile createBpmnFile() {
        return new MockMultipartFile(
                "file", "process.bpmn", "application/xml",
                "<bpmn>valid</bpmn>".getBytes(StandardCharsets.UTF_8));
    }

    private ProcessDefinition createTestDefinition() {
        return ProcessDefinition.create(
                "test-process", 1, "Test", "<bpmn/>",
                List.of(
                        new StartEvent("start", "Start", List.of(), List.of("f1")),
                        new EndEvent("end", "End", List.of("f1"), List.of(), null)
                ),
                List.of(new SequenceFlow("f1", "start", "end", null))
        );
    }

    @Test
    void shouldAllowAdminToDeployDefinition() throws Exception {
        // Arrange
        ProcessDefinition definition = createTestDefinition();
        when(bpmnParser.validate(anyString())).thenReturn(BpmnValidationResult.success());
        when(bpmnParser.parse(anyString())).thenReturn(List.of(definition));
        when(processEngine.deploy(any(ProcessDefinition.class))).thenReturn(definition);

        // Act & Assert
        mockMvc.perform(multipart("/api/v1/definitions").file(createBpmnFile())
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_PROCESS_ADMIN"))))
                .andExpect(status().isCreated());
    }

    @Test
    void shouldAllowDeployerToDeployDefinition() throws Exception {
        // Arrange
        ProcessDefinition definition = createTestDefinition();
        when(bpmnParser.validate(anyString())).thenReturn(BpmnValidationResult.success());
        when(bpmnParser.parse(anyString())).thenReturn(List.of(definition));
        when(processEngine.deploy(any(ProcessDefinition.class))).thenReturn(definition);

        // Act & Assert
        mockMvc.perform(multipart("/api/v1/definitions").file(createBpmnFile())
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_PROCESS_DEPLOYER"))))
                .andExpect(status().isCreated());
    }

    @Test
    void shouldDenyViewerToDeployDefinition() throws Exception {
        // Act & Assert
        mockMvc.perform(multipart("/api/v1/definitions").file(createBpmnFile())
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_PROCESS_VIEWER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldDenyUnauthenticatedDeploy() throws Exception {
        // Act & Assert
        mockMvc.perform(multipart("/api/v1/definitions").file(createBpmnFile()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldAllowViewerToListDefinitions() throws Exception {
        // Arrange
        when(definitionRepository.list()).thenReturn(List.of());

        // Act & Assert
        mockMvc.perform(get("/api/v1/definitions")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_PROCESS_VIEWER"))))
                .andExpect(status().isOk());
    }

    @Test
    void shouldDenyOperatorToDeleteDefinition() throws Exception {
        // Act & Assert
        mockMvc.perform(delete("/api/v1/definitions/order-process")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_PROCESS_OPERATOR"))))
                .andExpect(status().isForbidden());
    }
}
