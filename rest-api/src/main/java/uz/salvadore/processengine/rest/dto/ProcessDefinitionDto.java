package uz.salvadore.processengine.rest.dto;

import java.time.Instant;
import java.util.UUID;

public record ProcessDefinitionDto(
        UUID id,
        String key,
        int version,
        String name,
        Instant deployedAt
) {}
