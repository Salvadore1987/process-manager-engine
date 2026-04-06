package uz.salvadore.processengine.rest.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import uz.salvadore.processengine.core.domain.model.DeploymentBundle;
import uz.salvadore.processengine.core.domain.model.ProcessDefinition;
import uz.salvadore.processengine.core.port.outgoing.ProcessDefinitionStore;
import uz.salvadore.processengine.core.engine.ProcessEngine;
import uz.salvadore.processengine.core.parser.BpmnParser;
import uz.salvadore.processengine.core.parser.BpmnValidationResult;
import uz.salvadore.processengine.rest.dto.ProcessDefinitionDto;
import uz.salvadore.processengine.rest.dto.UnsupportedElementDto;
import uz.salvadore.processengine.rest.dto.ValidationResultDto;
import uz.salvadore.processengine.rest.exception.DefinitionNotFoundException;
import uz.salvadore.processengine.rest.exception.ValidationFailedException;
import uz.salvadore.processengine.rest.mapper.ProcessDefinitionDtoMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/definitions")
public class ProcessDefinitionController {

    private final ProcessEngine processEngine;
    private final ProcessDefinitionStore definitionStore;
    private final BpmnParser bpmnParser;
    private final ProcessDefinitionDtoMapper mapper;

    public ProcessDefinitionController(ProcessEngine processEngine,
                                       ProcessDefinitionStore definitionStore,
                                       BpmnParser bpmnParser,
                                       ProcessDefinitionDtoMapper mapper) {
        this.processEngine = processEngine;
        this.definitionStore = definitionStore;
        this.bpmnParser = bpmnParser;
        this.mapper = mapper;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ProcessDefinitionDto> deploy(@RequestParam("file") MultipartFile file)
            throws IOException {
        String bpmnXml = new String(file.getBytes(), StandardCharsets.UTF_8);

        BpmnValidationResult validationResult = bpmnParser.validate(bpmnXml);
        if (!validationResult.valid()) {
            throw new ValidationFailedException(validationResult);
        }

        List<ProcessDefinition> definitions = bpmnParser.parse(bpmnXml);
        ProcessDefinition definition = definitions.getFirst();
        ProcessDefinition deployed = processEngine.deploy(definition);

        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toDto(deployed));
    }

    @PostMapping(path = "/bundle", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<List<ProcessDefinitionDto>> deployBundle(
            @RequestParam("files") MultipartFile[] files) throws IOException {
        Map<String, String> bpmnFiles = new LinkedHashMap<>();
        for (MultipartFile file : files) {
            String fileName = file.getOriginalFilename();
            String bpmnXml = new String(file.getBytes(), StandardCharsets.UTF_8);

            BpmnValidationResult validationResult = bpmnParser.validate(bpmnXml);
            if (!validationResult.valid()) {
                throw new ValidationFailedException(validationResult);
            }

            bpmnFiles.put(fileName, bpmnXml);
        }

        DeploymentBundle bundle = new DeploymentBundle(bpmnFiles);
        List<ProcessDefinition> deployed = processEngine.deployBundle(bundle);

        List<ProcessDefinitionDto> dtos = deployed.stream()
                .map(mapper::toDto)
                .toList();

        return ResponseEntity.status(HttpStatus.CREATED).body(dtos);
    }

    @PostMapping(path = "/validate", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ValidationResultDto> validate(@RequestParam("file") MultipartFile file)
            throws IOException {
        String bpmnXml = new String(file.getBytes(), StandardCharsets.UTF_8);
        BpmnValidationResult result = bpmnParser.validate(bpmnXml);

        List<UnsupportedElementDto> elements = result.unsupportedElements().stream()
                .map(e -> new UnsupportedElementDto(e.element(), e.id(), e.name(), e.line()))
                .toList();

        return ResponseEntity.ok(new ValidationResultDto(result.valid(), elements));
    }

    @GetMapping
    public ResponseEntity<List<ProcessDefinitionDto>> list() {
        List<ProcessDefinitionDto> definitions = definitionStore.list().stream()
                .map(mapper::toDto)
                .toList();
        return ResponseEntity.ok(definitions);
    }

    @GetMapping("/{key}")
    public ResponseEntity<ProcessDefinitionDto> getByKey(@PathVariable String key) {
        ProcessDefinition definition = definitionStore.getByKey(key)
                .orElseThrow(() -> new DefinitionNotFoundException(key));
        return ResponseEntity.ok(mapper.toDto(definition));
    }

    @GetMapping("/{key}/versions")
    public ResponseEntity<List<ProcessDefinitionDto>> getVersions(@PathVariable String key) {
        List<ProcessDefinitionDto> versions = definitionStore.getVersions(key).stream()
                .map(mapper::toDto)
                .toList();
        return ResponseEntity.ok(versions);
    }

    @DeleteMapping("/{key}")
    public ResponseEntity<Void> undeploy(@PathVariable String key) {
        definitionStore.undeploy(key);
        return ResponseEntity.noContent().build();
    }
}
