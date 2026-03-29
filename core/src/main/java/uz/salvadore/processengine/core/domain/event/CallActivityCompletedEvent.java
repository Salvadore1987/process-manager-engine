package uz.salvadore.processengine.core.domain.event;

import java.time.Instant;
import java.util.UUID;

public record CallActivityCompletedEvent(
        UUID id,
        UUID processInstanceId,
        UUID tokenId,
        String nodeId,
        UUID childProcessInstanceId,
        Instant occurredAt,
        long sequenceNumber
) implements ProcessEvent {
}
