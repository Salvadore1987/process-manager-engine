package uz.salvadore.processengine.rest.dto;

import java.time.Instant;
import java.util.UUID;

public record ActivityHistoryDto(
        UUID tokenId,
        String nodeId,
        String state,
        Instant occurredAt
) {}
