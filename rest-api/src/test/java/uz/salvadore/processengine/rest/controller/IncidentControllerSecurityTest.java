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
import uz.salvadore.processengine.core.engine.ProcessDefinitionRepository;
import uz.salvadore.processengine.core.engine.ProcessEngine;
import uz.salvadore.processengine.core.parser.BpmnParser;
import uz.salvadore.processengine.core.port.outgoing.ProcessEventStore;
import uz.salvadore.processengine.rest.config.SecurityEnabledTestConfig;
import uz.salvadore.processengine.rest.dto.IncidentResolveDto;
import uz.salvadore.processengine.rest.mapper.ProcessDefinitionDtoMapper;
import uz.salvadore.processengine.rest.mapper.ProcessEventDtoMapper;
import uz.salvadore.processengine.rest.mapper.ProcessInstanceDtoMapper;

import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(IncidentController.class)
@Import({ProcessDefinitionDtoMapper.class, ProcessInstanceDtoMapper.class, ProcessEventDtoMapper.class, SecurityEnabledTestConfig.class})
class IncidentControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ProcessEngine processEngine;

    @MockBean
    private ProcessDefinitionRepository definitionRepository;

    @MockBean
    private BpmnParser bpmnParser;

    @MockBean
    private ProcessEventStore processEventStore;

    @Test
    void shouldAllowViewerToListIncidents() throws Exception {
        // Act & Assert
        mockMvc.perform(get("/api/v1/incidents")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_PROCESS_VIEWER"))))
                .andExpect(status().isOk());
    }

    @Test
    void shouldDenyViewerToResolveIncident() throws Exception {
        // Arrange
        UUID incidentId = UUID.randomUUID();
        IncidentResolveDto resolveDto = new IncidentResolveDto("RETRY");

        // Act & Assert
        mockMvc.perform(put("/api/v1/incidents/{id}/resolve", incidentId)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_PROCESS_VIEWER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(resolveDto)))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldAllowAdminToResolveIncident() throws Exception {
        // Arrange
        UUID incidentId = UUID.randomUUID();
        IncidentResolveDto resolveDto = new IncidentResolveDto("RETRY");

        // Act & Assert — admin can resolve, returns 404 because no actual incident exists (expected)
        mockMvc.perform(put("/api/v1/incidents/{id}/resolve", incidentId)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_PROCESS_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(resolveDto)))
                .andExpect(status().isNotFound());
    }
}
