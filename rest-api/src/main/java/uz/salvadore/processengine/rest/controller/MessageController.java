package uz.salvadore.processengine.rest.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uz.salvadore.processengine.core.engine.ProcessEngine;
import uz.salvadore.processengine.rest.dto.SendMessageRequestDto;

@RestController
@RequestMapping("/api/v1/messages")
public class MessageController {

    private final ProcessEngine processEngine;

    public MessageController(ProcessEngine processEngine) {
        this.processEngine = processEngine;
    }

    @PostMapping
    public ResponseEntity<Void> sendMessage(@RequestBody SendMessageRequestDto request) {
        processEngine.sendMessage(request.correlationId(), request.payload());
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }
}
