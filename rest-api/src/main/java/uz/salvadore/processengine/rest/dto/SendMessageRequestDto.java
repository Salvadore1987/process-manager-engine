package uz.salvadore.processengine.rest.dto;

import java.util.Map;
import java.util.UUID;

public record SendMessageRequestDto(
        UUID correlationId,
        Map<String, Object> payload
) {}
