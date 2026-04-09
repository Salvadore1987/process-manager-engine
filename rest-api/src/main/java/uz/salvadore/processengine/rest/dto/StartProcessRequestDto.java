package uz.salvadore.processengine.rest.dto;

import java.util.Map;

public record StartProcessRequestDto(
        String definitionKey,
        String businessKey,
        Map<String, Object> variables
) {}
