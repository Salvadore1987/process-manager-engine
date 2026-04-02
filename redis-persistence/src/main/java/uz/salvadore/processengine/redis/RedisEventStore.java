package uz.salvadore.processengine.redis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import uz.salvadore.processengine.core.domain.event.ProcessEvent;
import uz.salvadore.processengine.core.port.outgoing.ProcessEventStore;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Redis-backed implementation of ProcessEventStore.
 * Events are stored as JSON strings in Redis Lists keyed by process instance ID.
 */
public final class RedisEventStore implements ProcessEventStore {

    private static final String KEY_PREFIX = "pe:events:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public RedisEventStore(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void append(ProcessEvent event) {
        String key = KEY_PREFIX + event.processInstanceId();
        String json = serialize(event);
        redisTemplate.opsForList().rightPush(key, json);
    }

    @Override
    public void appendAll(List<ProcessEvent> events) {
        for (ProcessEvent event : events) {
            append(event);
        }
    }

    @Override
    public List<ProcessEvent> getEvents(UUID processInstanceId) {
        String key = KEY_PREFIX + processInstanceId;
        List<String> jsonEvents = redisTemplate.opsForList().range(key, 0, -1);
        if (jsonEvents == null || jsonEvents.isEmpty()) {
            return List.of();
        }
        return jsonEvents.stream()
                .map(this::deserialize)
                .sorted(Comparator.comparingLong(ProcessEvent::sequenceNumber))
                .toList();
    }

    private String serialize(ProcessEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize ProcessEvent: " + event.id(), e);
        }
    }

    private ProcessEvent deserialize(String json) {
        try {
            return objectMapper.readValue(json, ProcessEvent.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to deserialize ProcessEvent from JSON", e);
        }
    }
}
