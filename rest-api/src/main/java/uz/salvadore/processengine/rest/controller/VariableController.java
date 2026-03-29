package uz.salvadore.processengine.rest.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uz.salvadore.processengine.core.domain.model.ProcessInstance;
import uz.salvadore.processengine.core.engine.ProcessEngine;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/instances/{id}/variables")
public class VariableController {

    private final ProcessEngine processEngine;

    public VariableController(ProcessEngine processEngine) {
        this.processEngine = processEngine;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getVariables(@PathVariable UUID id) {
        ProcessInstance instance = processEngine.getProcessInstance(id);
        return ResponseEntity.ok(instance.getVariables());
    }

    @PutMapping
    public ResponseEntity<Map<String, Object>> updateVariables(@PathVariable UUID id,
                                                                @RequestBody Map<String, Object> variables) {
        ProcessInstance instance = processEngine.getProcessInstance(id);
        // Variables are updated through task completion; this returns current state
        return ResponseEntity.ok(instance.getVariables());
    }

    @GetMapping("/{name}")
    public ResponseEntity<Object> getVariable(@PathVariable UUID id, @PathVariable String name) {
        ProcessInstance instance = processEngine.getProcessInstance(id);
        Object value = instance.getVariables().get(name);
        if (value == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(value);
    }
}
