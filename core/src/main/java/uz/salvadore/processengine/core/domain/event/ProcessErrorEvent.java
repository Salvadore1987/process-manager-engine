package uz.salvadore.processengine.core.domain.event;

import java.time.Instant;
import java.util.UUID;

public record ProcessErrorEvent(
        UUID id,
        UUID processInstanceId,
        String errorCode,
        String errorMessage,
        Instant occurredAt,
        long sequenceNumber
) implements ProcessEvent {
}
