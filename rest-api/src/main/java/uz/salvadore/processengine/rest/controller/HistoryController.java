package uz.salvadore.processengine.rest.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uz.salvadore.processengine.core.domain.event.ProcessEvent;
import uz.salvadore.processengine.core.domain.event.TokenMovedEvent;
import uz.salvadore.processengine.core.domain.event.TaskCompletedEvent;
import uz.salvadore.processengine.core.port.outgoing.ProcessEventStore;
import uz.salvadore.processengine.rest.dto.ActivityHistoryDto;
import uz.salvadore.processengine.rest.dto.ProcessEventDto;
import uz.salvadore.processengine.rest.mapper.ProcessEventDtoMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/history")
public class HistoryController {

    private final ProcessEventStore eventStore;
    private final ProcessEventDtoMapper eventMapper;

    public HistoryController(ProcessEventStore eventStore, ProcessEventDtoMapper eventMapper) {
        this.eventStore = eventStore;
        this.eventMapper = eventMapper;
    }

    @GetMapping("/instances/{id}/events")
    public ResponseEntity<List<ProcessEventDto>> getEvents(@PathVariable UUID id) {
        List<ProcessEvent> events = eventStore.getEvents(id);
        List<ProcessEventDto> dtos = events.stream()
                .map(eventMapper::toDto)
                .toList();
        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/instances/{id}/activities")
    public ResponseEntity<List<ActivityHistoryDto>> getActivities(@PathVariable UUID id) {
        List<ProcessEvent> events = eventStore.getEvents(id);
        List<ActivityHistoryDto> activities = new ArrayList<>();

        for (ProcessEvent event : events) {
            if (event instanceof TokenMovedEvent moved) {
                activities.add(new ActivityHistoryDto(
                        moved.tokenId(), moved.toNodeId(), "MOVED", moved.occurredAt()));
            } else if (event instanceof TaskCompletedEvent completed) {
                activities.add(new ActivityHistoryDto(
                        completed.tokenId(), completed.nodeId(), "COMPLETED", completed.occurredAt()));
            }
        }

        return ResponseEntity.ok(activities);
    }
}
