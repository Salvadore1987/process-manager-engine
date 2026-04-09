package uz.salvadore.processengine.rest.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record ProcessInstanceDto(
        UUID id,
        UUID definitionId,
        UUID parentProcessInstanceId,
        String businessKey,
        String state,
        Map<String, Object> variables,
        Instant startedAt,
        Instant completedAt
) {}
