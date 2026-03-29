package uz.salvadore.processengine.core.domain.event;

import java.time.Instant;
import java.util.UUID;

public record TokenMovedEvent(
        UUID id,
        UUID processInstanceId,
        UUID tokenId,
        String fromNodeId,
        String toNodeId,
        Instant occurredAt,
        long sequenceNumber
) implements ProcessEvent {
}
