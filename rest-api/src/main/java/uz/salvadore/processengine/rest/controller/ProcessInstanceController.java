package uz.salvadore.processengine.rest.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uz.salvadore.processengine.core.domain.model.ProcessInstance;
import uz.salvadore.processengine.core.engine.ProcessEngine;
import uz.salvadore.processengine.core.port.outgoing.ProcessEventStore;
import uz.salvadore.processengine.rest.dto.PageDto;
import uz.salvadore.processengine.rest.dto.ProcessInstanceDto;
import uz.salvadore.processengine.rest.dto.StartProcessRequestDto;
import uz.salvadore.processengine.rest.mapper.ProcessInstanceDtoMapper;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/instances")
public class ProcessInstanceController {

    private final ProcessEngine processEngine;
    private final ProcessInstanceDtoMapper mapper;

    public ProcessInstanceController(ProcessEngine processEngine,
                                     ProcessInstanceDtoMapper mapper) {
        this.processEngine = processEngine;
        this.mapper = mapper;
    }

    @PostMapping
    public ResponseEntity<ProcessInstanceDto> start(@RequestBody StartProcessRequestDto request) {
        ProcessInstance instance = processEngine.startProcess(
                request.definitionKey(), request.variables());
        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toDto(instance));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProcessInstanceDto> getById(@PathVariable UUID id) {
        ProcessInstance instance = processEngine.getProcessInstance(id);
        return ResponseEntity.ok(mapper.toDto(instance));
    }

    @PutMapping("/{id}/suspend")
    public ResponseEntity<ProcessInstanceDto> suspend(@PathVariable UUID id) {
        ProcessInstance instance = processEngine.suspendProcess(id);
        return ResponseEntity.ok(mapper.toDto(instance));
    }

    @PutMapping("/{id}/resume")
    public ResponseEntity<ProcessInstanceDto> resume(@PathVariable UUID id) {
        ProcessInstance instance = processEngine.resumeProcess(id);
        return ResponseEntity.ok(mapper.toDto(instance));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ProcessInstanceDto> terminate(@PathVariable UUID id) {
        ProcessInstance instance = processEngine.terminateProcess(id);
        return ResponseEntity.ok(mapper.toDto(instance));
    }
}
