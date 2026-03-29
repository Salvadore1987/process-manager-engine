package uz.salvadore.processengine.rest.dto;

import java.time.Instant;
import java.util.UUID;

public record IncidentDto(
        UUID id,
        UUID processInstanceId,
        String type,
        String message,
        Instant occurredAt,
        boolean resolved
) {}
