package uz.salvadore.processengine.core.domain.event;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record TaskCompletedEvent(
        UUID id,
        UUID processInstanceId,
        UUID tokenId,
        String nodeId,
        Map<String, Object> result,
        Instant occurredAt,
        long sequenceNumber
) implements ProcessEvent {
}
