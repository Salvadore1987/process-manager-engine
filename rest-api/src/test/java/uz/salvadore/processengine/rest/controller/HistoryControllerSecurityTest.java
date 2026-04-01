package uz.salvadore.processengine.rest.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import uz.salvadore.processengine.core.port.outgoing.ProcessDefinitionStore;
import uz.salvadore.processengine.core.engine.ProcessEngine;
import uz.salvadore.processengine.core.parser.BpmnParser;
import uz.salvadore.processengine.core.port.outgoing.ProcessEventStore;
import uz.salvadore.processengine.rest.config.SecurityEnabledTestConfig;
import uz.salvadore.processengine.rest.mapper.ProcessDefinitionDtoMapper;
import uz.salvadore.processengine.rest.mapper.ProcessEventDtoMapper;
import uz.salvadore.processengine.rest.mapper.ProcessInstanceDtoMapper;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(HistoryController.class)
@Import({ProcessDefinitionDtoMapper.class, ProcessInstanceDtoMapper.class, ProcessEventDtoMapper.class, SecurityEnabledTestConfig.class})
class HistoryControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ProcessEngine processEngine;

    @MockBean
    private ProcessDefinitionStore definitionRepository;

    @MockBean
    private BpmnParser bpmnParser;

    @MockBean
    private ProcessEventStore processEventStore;

    @Test
    void shouldAllowViewerToAccessEvents() throws Exception {
        // Arrange
        UUID instanceId = UUID.randomUUID();
        when(processEventStore.getEvents(instanceId)).thenReturn(List.of());

        // Act & Assert
        mockMvc.perform(get("/api/v1/history/instances/{id}/events", instanceId)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_PROCESS_VIEWER"))))
                .andExpect(status().isOk());
    }

    @Test
    void shouldDenyDeployerToAccessEvents() throws Exception {
        // Arrange
        UUID instanceId = UUID.randomUUID();

        // Act & Assert
        mockMvc.perform(get("/api/v1/history/instances/{id}/events", instanceId)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_PROCESS_DEPLOYER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldDenyUnauthenticatedAccessToEvents() throws Exception {
        // Arrange
        UUID instanceId = UUID.randomUUID();

        // Act & Assert
        mockMvc.perform(get("/api/v1/history/instances/{id}/events", instanceId))
                .andExpect(status().isUnauthorized());
    }
}
