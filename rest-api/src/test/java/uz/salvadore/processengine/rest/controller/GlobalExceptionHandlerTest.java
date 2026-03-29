package uz.salvadore.processengine.rest.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import uz.salvadore.processengine.core.engine.ProcessDefinitionRepository;
import uz.salvadore.processengine.core.engine.ProcessEngine;
import uz.salvadore.processengine.core.parser.BpmnParser;
import uz.salvadore.processengine.core.port.outgoing.ProcessEventStore;
import uz.salvadore.processengine.rest.exception.ProcessNotFoundException;
import uz.salvadore.processengine.rest.exception.DefinitionNotFoundException;
import uz.salvadore.processengine.rest.mapper.ProcessDefinitionDtoMapper;
import uz.salvadore.processengine.rest.mapper.ProcessInstanceDtoMapper;
import uz.salvadore.processengine.rest.mapper.ProcessEventDtoMapper;

import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests for {@link uz.salvadore.processengine.rest.exception.GlobalExceptionHandler}.
 * Exercises exception handling through actual controller endpoints.
 */
@WebMvcTest(ProcessInstanceController.class)
@Import({ProcessDefinitionDtoMapper.class, ProcessInstanceDtoMapper.class, ProcessEventDtoMapper.class})
class GlobalExceptionHandlerTest {

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

    @Test
    void shouldReturn404WithErrorResponseForProcessNotFoundException() throws Exception {
        // Arrange
        UUID processInstanceId = UUID.randomUUID();
        when(processEngine.getProcessInstance(processInstanceId))
                .thenThrow(new ProcessNotFoundException(processInstanceId));

        // Act & Assert
        mockMvc.perform(get("/api/v1/instances/{id}", processInstanceId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Process instance not found: " + processInstanceId))
                .andExpect(jsonPath("$.timestamp").value(notNullValue()))
                .andExpect(jsonPath("$.path").value("/api/v1/instances/" + processInstanceId));
    }

    @Test
    void shouldReturn404WithErrorResponseForDefinitionNotFoundException() throws Exception {
        // Arrange
        when(definitionRepository.getByKey("missing-def")).thenReturn(Optional.empty());

        // Act & Assert
        // We test DefinitionNotFoundException through the ProcessDefinitionController,
        // but since this test class uses ProcessInstanceController, we use the engine mock
        // to throw DefinitionNotFoundException indirectly. Let's use a different approach:
        // we test it through the engine throwing the exception.
        UUID processInstanceId = UUID.randomUUID();
        when(processEngine.getProcessInstance(processInstanceId))
                .thenThrow(new DefinitionNotFoundException("missing-def"));

        mockMvc.perform(get("/api/v1/instances/{id}", processInstanceId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Process definition not found: missing-def"))
                .andExpect(jsonPath("$.timestamp").value(notNullValue()))
                .andExpect(jsonPath("$.path").value("/api/v1/instances/" + processInstanceId));
    }

    @Test
    void shouldReturn409WithErrorResponseForIllegalStateException() throws Exception {
        // Arrange
        UUID processInstanceId = UUID.randomUUID();
        when(processEngine.suspendProcess(processInstanceId))
                .thenThrow(new IllegalStateException("Cannot suspend process in state COMPLETED"));

        // Act & Assert
        mockMvc.perform(put("/api/v1/instances/{id}/suspend", processInstanceId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("CONFLICT"))
                .andExpect(jsonPath("$.message").value("Cannot suspend process in state COMPLETED"))
                .andExpect(jsonPath("$.timestamp").value(notNullValue()))
                .andExpect(jsonPath("$.path").value("/api/v1/instances/" + processInstanceId + "/suspend"));
    }

    @Test
    void shouldReturn400WithErrorResponseForIllegalArgumentException() throws Exception {
        // Arrange
        UUID processInstanceId = UUID.randomUUID();
        when(processEngine.getProcessInstance(processInstanceId))
                .thenThrow(new IllegalArgumentException("Invalid process instance ID format"));

        // Act & Assert
        mockMvc.perform(get("/api/v1/instances/{id}", processInstanceId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value("Invalid process instance ID format"))
                .andExpect(jsonPath("$.timestamp").value(notNullValue()))
                .andExpect(jsonPath("$.path").value("/api/v1/instances/" + processInstanceId));
    }
}
