package uz.salvadore.processengine.core.domain.event;

import java.time.Instant;
import java.util.UUID;

public record ProcessCompletedEvent(
        UUID id,
        UUID processInstanceId,
        Instant occurredAt,
        long sequenceNumber
) implements ProcessEvent {
}
