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
import uz.salvadore.processengine.rest.dto.SendMessageRequestDto;
import uz.salvadore.processengine.rest.mapper.ProcessDefinitionDtoMapper;
import uz.salvadore.processengine.rest.mapper.ProcessEventDtoMapper;
import uz.salvadore.processengine.rest.mapper.ProcessInstanceDtoMapper;

import java.util.Map;
import java.util.UUID;

import static org.mockito.Mockito.doNothing;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MessageController.class)
@Import({ProcessDefinitionDtoMapper.class, ProcessInstanceDtoMapper.class, ProcessEventDtoMapper.class, SecurityEnabledTestConfig.class})
class MessageControllerSecurityTest {

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
    void shouldAllowOperatorToSendMessage() throws Exception {
        // Arrange
        UUID correlationId = UUID.randomUUID();
        SendMessageRequestDto request = new SendMessageRequestDto(correlationId, Map.of("approved", true));
        doNothing().when(processEngine).sendMessage(correlationId, Map.of("approved", true));

        // Act & Assert
        mockMvc.perform(post("/api/v1/messages")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_PROCESS_OPERATOR")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted());
    }

    @Test
    void shouldDenyViewerToSendMessage() throws Exception {
        // Arrange
        SendMessageRequestDto request = new SendMessageRequestDto(UUID.randomUUID(), Map.of());

        // Act & Assert
        mockMvc.perform(post("/api/v1/messages")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_PROCESS_VIEWER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldDenyUnauthenticatedSendMessage() throws Exception {
        // Arrange
        SendMessageRequestDto request = new SendMessageRequestDto(UUID.randomUUID(), Map.of());

        // Act & Assert
        mockMvc.perform(post("/api/v1/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }
}
