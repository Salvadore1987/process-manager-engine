package uz.salvadore.processengine.core.domain.event;

import java.time.Instant;
import java.util.UUID;

public record CompensationTriggeredEvent(
        UUID id,
        UUID processInstanceId,
        String sourceNodeId,
        String compensationTaskId,
        Instant occurredAt,
        long sequenceNumber
) implements ProcessEvent {
}
