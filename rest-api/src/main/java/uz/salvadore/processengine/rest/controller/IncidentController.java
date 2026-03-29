package uz.salvadore.processengine.rest.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uz.salvadore.processengine.rest.dto.IncidentDto;
import uz.salvadore.processengine.rest.dto.IncidentResolveDto;

import java.util.List;
import java.util.UUID;

/**
 * Incident controller. Incidents are created by the DeadLetterConsumer
 * when messages exhaust their retry attempts. Currently backed by an
 * in-memory list (placeholder for future persistence).
 */
@RestController
@RequestMapping("/api/v1/incidents")
public class IncidentController {

    @GetMapping
    public ResponseEntity<List<IncidentDto>> list() {
        return ResponseEntity.ok(List.of());
    }

    @GetMapping("/{id}")
    public ResponseEntity<IncidentDto> getById(@PathVariable UUID id) {
        return ResponseEntity.notFound().build();
    }

    @PutMapping("/{id}/resolve")
    public ResponseEntity<IncidentDto> resolve(@PathVariable UUID id,
                                                @RequestBody IncidentResolveDto request) {
        return ResponseEntity.notFound().build();
    }
}
