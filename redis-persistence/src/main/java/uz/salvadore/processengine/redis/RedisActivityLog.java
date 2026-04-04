package uz.salvadore.processengine.redis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import uz.salvadore.processengine.core.port.outgoing.ActivityLog;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Redis-backed activity log. Stores business-level task events as a Hash
 * keyed by process instance ID, with nodeId as field.
 */
public final class RedisActivityLog implements ActivityLog {

    private static final String KEY_PREFIX = "pe:activity-log:";
    private static final String PROCESS_FIELD = "_process";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public RedisActivityLog(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void taskStarted(UUID processInstanceId, String nodeId, String topic, Instant startedAt) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("nodeId", nodeId);
        entry.put("topic", topic);
        entry.put("status", "STARTED");
        entry.put("startedAt", startedAt.toString());
        putEntry(processInstanceId, nodeId, entry);
    }

    @Override
    public void taskCompleted(UUID processInstanceId, String nodeId, Map<String, Object> result, Instant completedAt) {
        String key = KEY_PREFIX + processInstanceId;
        String existing = (String) redisTemplate.opsForHash().get(key, nodeId);
        Map<String, Object> entry = existing != null ? deserialize(existing) : new LinkedHashMap<>();
        entry.put("status", "COMPLETED");
        entry.put("completedAt", completedAt.toString());
        if (result != null && !result.isEmpty()) {
            entry.put("result", result);
        }
        putEntry(processInstanceId, nodeId, entry);
    }

    @Override
    public void taskFailed(UUID processInstanceId, String nodeId, String errorCode, String errorMessage, Instant occurredAt) {
        String key = KEY_PREFIX + processInstanceId;
        String existing = (String) redisTemplate.opsForHash().get(key, nodeId);
        Map<String, Object> entry = existing != null ? deserialize(existing) : new LinkedHashMap<>();
        entry.put("status", "FAILED");
        entry.put("errorCode", errorCode);
        entry.put("errorMessage", errorMessage);
        entry.put("failedAt", occurredAt.toString());
        putEntry(processInstanceId, nodeId, entry);
    }

    @Override
    public void processCompleted(UUID processInstanceId, Instant completedAt) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("status", "COMPLETED");
        entry.put("completedAt", completedAt.toString());
        putEntry(processInstanceId, PROCESS_FIELD, entry);
    }

    @Override
    public void processErrored(UUID processInstanceId, String errorCode, String errorMessage, Instant occurredAt) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("status", "ERROR");
        entry.put("errorCode", errorCode);
        entry.put("errorMessage", errorMessage);
        entry.put("occurredAt", occurredAt.toString());
        putEntry(processInstanceId, PROCESS_FIELD, entry);
    }

    private void putEntry(UUID processInstanceId, String field, Map<String, Object> entry) {
        String key = KEY_PREFIX + processInstanceId;
        redisTemplate.opsForHash().put(key, field, serialize(entry));
    }

    private String serialize(Map<String, Object> entry) {
        try {
            return objectMapper.writeValueAsString(entry);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize activity log entry", e);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> deserialize(String json) {
        try {
            return objectMapper.readValue(json, LinkedHashMap.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to deserialize activity log entry", e);
        }
    }
}
