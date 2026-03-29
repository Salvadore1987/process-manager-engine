package uz.salvadore.processengine.core.domain.event;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record ProcessStartedEvent(
        UUID id,
        UUID processInstanceId,
        UUID definitionId,
        UUID parentProcessInstanceId,
        Map<String, Object> variables,
        Instant occurredAt,
        long sequenceNumber
) implements ProcessEvent {
}
