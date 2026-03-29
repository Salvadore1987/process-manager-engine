package uz.salvadore.processengine.rest.dto;

import java.time.Instant;
import java.util.UUID;

public record ProcessEventDto(
        UUID id,
        UUID processInstanceId,
        String type,
        Instant occurredAt,
        long sequenceNumber
) {}
