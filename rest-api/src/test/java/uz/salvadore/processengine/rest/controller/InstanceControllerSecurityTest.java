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
import uz.salvadore.processengine.rest.dto.StartProcessRequestDto;
import uz.salvadore.processengine.rest.mapper.ProcessDefinitionDtoMapper;
import uz.salvadore.processengine.rest.mapper.ProcessEventDtoMapper;
import uz.salvadore.processengine.rest.mapper.ProcessInstanceDtoMapper;

import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProcessInstanceController.class)
@Import({ProcessDefinitionDtoMapper.class, ProcessInstanceDtoMapper.class, ProcessEventDtoMapper.class, SecurityEnabledTestConfig.class})
class InstanceControllerSecurityTest {

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
    void shouldAllowOperatorToStartInstance() throws Exception {
        // Arrange
        UUID definitionId = UUID.randomUUID();
        ProcessInstance instance = ProcessInstance.create(definitionId, Map.of("orderId", "123"));
        StartProcessRequestDto request = new StartProcessRequestDto("order-processing", Map.of("orderId", "123"));
        when(processEngine.startProcess(eq("order-processing"), anyMap())).thenReturn(instance);

        // Act & Assert
        mockMvc.perform(post("/api/v1/instances")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_PROCESS_OPERATOR")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    @Test
    void shouldDenyViewerToStartInstance() throws Exception {
        // Arrange
        StartProcessRequestDto request = new StartProcessRequestDto("order-processing", Map.of());

        // Act & Assert
        mockMvc.perform(post("/api/v1/instances")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_PROCESS_VIEWER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldAllowViewerToGetInstance() throws Exception {
        // Arrange
        UUID instanceId = UUID.randomUUID();
        UUID definitionId = UUID.randomUUID();
        ProcessInstance instance = ProcessInstance.create(definitionId, Map.of());
        when(processEngine.getProcessInstance(instanceId)).thenReturn(instance);

        // Act & Assert
        mockMvc.perform(get("/api/v1/instances/{id}", instanceId)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_PROCESS_VIEWER"))))
                .andExpect(status().isOk());
    }

    @Test
    void shouldDenyViewerToSuspendInstance() throws Exception {
        // Arrange
        UUID instanceId = UUID.randomUUID();

        // Act & Assert
        mockMvc.perform(put("/api/v1/instances/{id}/suspend", instanceId)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_PROCESS_VIEWER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldDenyUnauthenticatedTerminate() throws Exception {
        // Arrange
        UUID instanceId = UUID.randomUUID();

        // Act & Assert
        mockMvc.perform(delete("/api/v1/instances/{id}", instanceId))
                .andExpect(status().isUnauthorized());
    }
}
