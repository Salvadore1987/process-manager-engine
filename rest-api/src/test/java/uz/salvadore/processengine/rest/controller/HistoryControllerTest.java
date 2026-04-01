package uz.salvadore.processengine.rest.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import uz.salvadore.processengine.core.domain.event.ProcessEvent;
import uz.salvadore.processengine.core.domain.event.ProcessStartedEvent;
import uz.salvadore.processengine.core.domain.event.TaskCompletedEvent;
import uz.salvadore.processengine.core.domain.event.TokenMovedEvent;
import uz.salvadore.processengine.core.port.outgoing.ProcessDefinitionStore;
import uz.salvadore.processengine.core.engine.ProcessEngine;
import uz.salvadore.processengine.core.parser.BpmnParser;
import uz.salvadore.processengine.core.port.outgoing.ProcessEventStore;
import uz.salvadore.processengine.rest.mapper.ProcessDefinitionDtoMapper;
import uz.salvadore.processengine.rest.mapper.ProcessInstanceDtoMapper;
import uz.salvadore.processengine.rest.mapper.ProcessEventDtoMapper;
import uz.salvadore.processengine.rest.config.NoSecurityTestConfig;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(HistoryController.class)
@Import({ProcessDefinitionDtoMapper.class, ProcessInstanceDtoMapper.class, ProcessEventDtoMapper.class, NoSecurityTestConfig.class})
class HistoryControllerTest {

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
    void shouldGetEventsAndReturn200() throws Exception {
        // Arrange
        UUID processInstanceId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        Instant occurredAt = Instant.parse("2026-03-29T10:00:00Z");

        ProcessStartedEvent startedEvent = new ProcessStartedEvent(
                eventId, processInstanceId, UUID.randomUUID(), null,
                Map.of("key", "value"), occurredAt, 1L);

        when(processEventStore.getEvents(processInstanceId)).thenReturn(List.of(startedEvent));

        // Act & Assert
        mockMvc.perform(get("/api/v1/history/instances/{id}/events", processInstanceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(eventId.toString()))
                .andExpect(jsonPath("$[0].processInstanceId").value(processInstanceId.toString()))
                .andExpect(jsonPath("$[0].type").value("ProcessStartedEvent"))
                .andExpect(jsonPath("$[0].occurredAt").value("2026-03-29T10:00:00Z"))
                .andExpect(jsonPath("$[0].sequenceNumber").value(1));
    }

    @Test
    void shouldGetEmptyEventsListAndReturn200() throws Exception {
        // Arrange
        UUID processInstanceId = UUID.randomUUID();
        when(processEventStore.getEvents(processInstanceId)).thenReturn(List.of());

        // Act & Assert
        mockMvc.perform(get("/api/v1/history/instances/{id}/events", processInstanceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void shouldGetActivitiesFromTokenMovedAndTaskCompletedEvents() throws Exception {
        // Arrange
        UUID processInstanceId = UUID.randomUUID();
        UUID tokenId = UUID.randomUUID();
        Instant movedAt = Instant.parse("2026-03-29T10:00:00Z");
        Instant completedAt = Instant.parse("2026-03-29T10:01:00Z");

        TokenMovedEvent movedEvent = new TokenMovedEvent(
                UUID.randomUUID(), processInstanceId, tokenId,
                "start", "task1", movedAt, 1L);

        TaskCompletedEvent completedEvent = new TaskCompletedEvent(
                UUID.randomUUID(), processInstanceId, tokenId,
                "task1", Map.of("result", "done"), completedAt, 2L);

        List<ProcessEvent> events = List.of(movedEvent, completedEvent);
        when(processEventStore.getEvents(processInstanceId)).thenReturn(events);

        // Act & Assert
        mockMvc.perform(get("/api/v1/history/instances/{id}/activities", processInstanceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].tokenId").value(tokenId.toString()))
                .andExpect(jsonPath("$[0].nodeId").value("task1"))
                .andExpect(jsonPath("$[0].state").value("MOVED"))
                .andExpect(jsonPath("$[0].occurredAt").value("2026-03-29T10:00:00Z"))
                .andExpect(jsonPath("$[1].tokenId").value(tokenId.toString()))
                .andExpect(jsonPath("$[1].nodeId").value("task1"))
                .andExpect(jsonPath("$[1].state").value("COMPLETED"))
                .andExpect(jsonPath("$[1].occurredAt").value("2026-03-29T10:01:00Z"));
    }

    @Test
    void shouldReturnEmptyActivitiesWhenNoTokenMovedOrTaskCompletedEvents() throws Exception {
        // Arrange
        UUID processInstanceId = UUID.randomUUID();
        ProcessStartedEvent startedEvent = new ProcessStartedEvent(
                UUID.randomUUID(), processInstanceId, UUID.randomUUID(), null,
                Map.of(), Instant.now(), 1L);

        when(processEventStore.getEvents(processInstanceId)).thenReturn(List.of(startedEvent));

        // Act & Assert
        mockMvc.perform(get("/api/v1/history/instances/{id}/activities", processInstanceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }
}
