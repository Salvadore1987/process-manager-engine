package uz.salvadore.processengine.rest.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import uz.salvadore.processengine.core.domain.model.ProcessInstance;
import uz.salvadore.processengine.core.port.outgoing.ProcessDefinitionStore;
import uz.salvadore.processengine.core.engine.ProcessEngine;
import uz.salvadore.processengine.core.parser.BpmnParser;
import uz.salvadore.processengine.core.port.outgoing.ProcessEventStore;
import uz.salvadore.processengine.rest.dto.StartProcessRequestDto;
import uz.salvadore.processengine.rest.mapper.ProcessDefinitionDtoMapper;
import uz.salvadore.processengine.rest.mapper.ProcessInstanceDtoMapper;
import uz.salvadore.processengine.rest.mapper.ProcessEventDtoMapper;
import uz.salvadore.processengine.rest.config.NoSecurityTestConfig;

import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProcessInstanceController.class)
@Import({ProcessDefinitionDtoMapper.class, ProcessInstanceDtoMapper.class, ProcessEventDtoMapper.class, NoSecurityTestConfig.class})
class ProcessInstanceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ProcessEngine processEngine;

    @MockBean
    private ProcessDefinitionStore definitionRepository;

    @MockBean
    private BpmnParser bpmnParser;

    @MockBean
    private ProcessEventStore processEventStore;

    private ProcessInstance createTestInstance() {
        UUID definitionId = UUID.randomUUID();
        return ProcessInstance.create(definitionId, Map.of("orderId", "12345"));
    }

    @Test
    void shouldStartProcessAndReturn201() throws Exception {
        // Arrange
        ProcessInstance instance = createTestInstance();
        StartProcessRequestDto request = new StartProcessRequestDto(
                "order-processing", Map.of("orderId", "12345"));

        when(processEngine.startProcess(eq("order-processing"), anyMap())).thenReturn(instance);

        // Act & Assert
        mockMvc.perform(post("/api/v1/instances")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(instance.getId().toString()))
                .andExpect(jsonPath("$.definitionId").value(instance.getDefinitionId().toString()))
                .andExpect(jsonPath("$.state").value("RUNNING"))
                .andExpect(jsonPath("$.variables.orderId").value("12345"))
                .andExpect(jsonPath("$.startedAt").value(notNullValue()))
                .andExpect(jsonPath("$.completedAt").value(nullValue()));
    }

    @Test
    void shouldGetProcessInstanceByIdAndReturn200() throws Exception {
        // Arrange
        ProcessInstance instance = createTestInstance();
        when(processEngine.getProcessInstance(instance.getId())).thenReturn(instance);

        // Act & Assert
        mockMvc.perform(get("/api/v1/instances/{id}", instance.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(instance.getId().toString()))
                .andExpect(jsonPath("$.state").value("RUNNING"));
    }

    @Test
    void shouldSuspendProcessAndReturn200() throws Exception {
        // Arrange
        ProcessInstance instance = createTestInstance();
        ProcessInstance suspendedInstance = instance.suspend();
        when(processEngine.suspendProcess(instance.getId())).thenReturn(suspendedInstance);

        // Act & Assert
        mockMvc.perform(put("/api/v1/instances/{id}/suspend", instance.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(suspendedInstance.getId().toString()))
                .andExpect(jsonPath("$.state").value("SUSPENDED"));
    }

    @Test
    void shouldResumeProcessAndReturn200() throws Exception {
        // Arrange
        ProcessInstance instance = createTestInstance();
        ProcessInstance suspendedInstance = instance.suspend();
        ProcessInstance resumedInstance = suspendedInstance.resume();
        when(processEngine.resumeProcess(instance.getId())).thenReturn(resumedInstance);

        // Act & Assert
        mockMvc.perform(put("/api/v1/instances/{id}/resume", instance.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(resumedInstance.getId().toString()))
                .andExpect(jsonPath("$.state").value("RUNNING"));
    }

    @Test
    void shouldTerminateProcessAndReturn200() throws Exception {
        // Arrange
        ProcessInstance instance = createTestInstance();
        when(processEngine.terminateProcess(instance.getId())).thenReturn(instance);

        // Act & Assert
        mockMvc.perform(delete("/api/v1/instances/{id}", instance.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(instance.getId().toString()));
    }
}
