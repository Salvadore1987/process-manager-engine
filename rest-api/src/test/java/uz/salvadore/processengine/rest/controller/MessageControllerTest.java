package uz.salvadore.processengine.rest.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import uz.salvadore.processengine.core.engine.ProcessDefinitionRepository;
import uz.salvadore.processengine.core.engine.ProcessEngine;
import uz.salvadore.processengine.core.parser.BpmnParser;
import uz.salvadore.processengine.core.port.outgoing.ProcessEventStore;
import uz.salvadore.processengine.rest.dto.SendMessageRequestDto;
import uz.salvadore.processengine.rest.mapper.ProcessDefinitionDtoMapper;
import uz.salvadore.processengine.rest.mapper.ProcessInstanceDtoMapper;
import uz.salvadore.processengine.rest.mapper.ProcessEventDtoMapper;

import java.util.Map;
import java.util.UUID;

import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MessageController.class)
@Import({ProcessDefinitionDtoMapper.class, ProcessInstanceDtoMapper.class, ProcessEventDtoMapper.class})
class MessageControllerTest {

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
    void shouldSendMessageAndReturn202() throws Exception {
        // Arrange
        UUID correlationId = UUID.randomUUID();
        Map<String, Object> payload = Map.of("approved", true, "comment", "Looks good");
        SendMessageRequestDto request = new SendMessageRequestDto(correlationId, payload);

        doNothing().when(processEngine).sendMessage(correlationId, payload);

        // Act & Assert
        mockMvc.perform(post("/api/v1/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted());

        verify(processEngine).sendMessage(correlationId, payload);
    }
}
