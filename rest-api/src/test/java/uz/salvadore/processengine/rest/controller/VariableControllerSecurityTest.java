package uz.salvadore.processengine.rest.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import uz.salvadore.processengine.core.domain.model.ProcessInstance;
import uz.salvadore.processengine.core.port.outgoing.ProcessDefinitionStore;
import uz.salvadore.processengine.core.engine.ProcessEngine;
import uz.salvadore.processengine.core.parser.BpmnParser;
import uz.salvadore.processengine.core.port.outgoing.ProcessEventStore;
import uz.salvadore.processengine.rest.config.SecurityEnabledTestConfig;
import uz.salvadore.processengine.rest.mapper.ProcessDefinitionDtoMapper;
import uz.salvadore.processengine.rest.mapper.ProcessEventDtoMapper;
import uz.salvadore.processengine.rest.mapper.ProcessInstanceDtoMapper;

import java.util.Map;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(VariableController.class)
@Import({ProcessDefinitionDtoMapper.class, ProcessInstanceDtoMapper.class, ProcessEventDtoMapper.class, SecurityEnabledTestConfig.class})
class VariableControllerSecurityTest {

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
    void shouldAllowViewerToGetVariables() throws Exception {
        // Arrange
        UUID instanceId = UUID.randomUUID();
        ProcessInstance instance = ProcessInstance.create(UUID.randomUUID(), Map.of("key", "value"));
        when(processEngine.getProcessInstance(instanceId)).thenReturn(instance);

        // Act & Assert
        mockMvc.perform(get("/api/v1/instances/{id}/variables", instanceId)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_PROCESS_VIEWER"))))
                .andExpect(status().isOk());
    }

    @Test
    void shouldDenyViewerToUpdateVariables() throws Exception {
        // Arrange
        UUID instanceId = UUID.randomUUID();

        // Act & Assert
        mockMvc.perform(put("/api/v1/instances/{id}/variables", instanceId)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_PROCESS_VIEWER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("key", "new-value"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldAllowOperatorToUpdateVariables() throws Exception {
        // Arrange
        UUID instanceId = UUID.randomUUID();
        ProcessInstance instance = ProcessInstance.create(UUID.randomUUID(), Map.of("key", "value"));
        when(processEngine.getProcessInstance(instanceId)).thenReturn(instance);

        // Act & Assert
        mockMvc.perform(put("/api/v1/instances/{id}/variables", instanceId)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_PROCESS_OPERATOR")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("key", "new-value"))))
                .andExpect(status().isOk());
    }
}
