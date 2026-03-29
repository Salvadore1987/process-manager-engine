package uz.salvadore.processengine.rest.dto;

import java.util.Map;

public record StartProcessRequestDto(
        String definitionKey,
        Map<String, Object> variables
) {}
