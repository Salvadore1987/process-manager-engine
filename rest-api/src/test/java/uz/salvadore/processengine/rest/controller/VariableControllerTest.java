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
import uz.salvadore.processengine.rest.mapper.ProcessDefinitionDtoMapper;
import uz.salvadore.processengine.rest.mapper.ProcessInstanceDtoMapper;
import uz.salvadore.processengine.rest.mapper.ProcessEventDtoMapper;
import uz.salvadore.processengine.rest.config.NoSecurityTestConfig;

import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(VariableController.class)
@Import({ProcessDefinitionDtoMapper.class, ProcessInstanceDtoMapper.class, ProcessEventDtoMapper.class, NoSecurityTestConfig.class})
class VariableControllerTest {

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

    @Test
    void shouldGetVariablesAndReturn200() throws Exception {
        // Arrange
        UUID definitionId = UUID.randomUUID();
        ProcessInstance instance = ProcessInstance.create(definitionId, "test-biz-key", Map.of("orderId", "12345", "amount", 100));
        when(processEngine.getProcessInstance(instance.getId())).thenReturn(instance);

        // Act & Assert
        mockMvc.perform(get("/api/v1/instances/{id}/variables", instance.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value("12345"))
                .andExpect(jsonPath("$.amount").value(100));
    }

    @Test
    void shouldGetVariableByNameAndReturn200() throws Exception {
        // Arrange
        UUID definitionId = UUID.randomUUID();
        ProcessInstance instance = ProcessInstance.create(definitionId, "test-biz-key", Map.of("orderId", "12345"));
        when(processEngine.getProcessInstance(instance.getId())).thenReturn(instance);

        // Act & Assert
        mockMvc.perform(get("/api/v1/instances/{id}/variables/{name}", instance.getId(), "orderId"))
                .andExpect(status().isOk());
    }

    @Test
    void shouldReturn404WhenVariableNotFoundByName() throws Exception {
        // Arrange
        UUID definitionId = UUID.randomUUID();
        ProcessInstance instance = ProcessInstance.create(definitionId, "test-biz-key", Map.of("orderId", "12345"));
        when(processEngine.getProcessInstance(instance.getId())).thenReturn(instance);

        // Act & Assert
        mockMvc.perform(get("/api/v1/instances/{id}/variables/{name}", instance.getId(), "nonExistent"))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldUpdateVariablesAndReturn200() throws Exception {
        // Arrange
        UUID definitionId = UUID.randomUUID();
        ProcessInstance instance = ProcessInstance.create(definitionId, "test-biz-key", Map.of("orderId", "12345"));
        when(processEngine.getProcessInstance(instance.getId())).thenReturn(instance);

        Map<String, Object> newVariables = Map.of("orderId", "99999", "status", "updated");

        // Act & Assert
        mockMvc.perform(put("/api/v1/instances/{id}/variables", instance.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(newVariables)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value("12345"));
    }
}
