package uz.salvadore.processengine.redis;

import org.springframework.data.redis.core.StringRedisTemplate;
import uz.salvadore.processengine.core.port.outgoing.InstanceDefinitionMapping;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Redis-backed implementation of InstanceDefinitionMapping.
 * Maps process instance IDs to their definition IDs using Redis strings and a set index.
 */
public final class RedisInstanceDefinitionMapping implements InstanceDefinitionMapping {

    private static final String KEY_PREFIX = "pe:inst-def:";
    private static final String KEY_ALL = "pe:instances";

    private final StringRedisTemplate redisTemplate;

    public RedisInstanceDefinitionMapping(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void put(UUID instanceId, UUID definitionId) {
        redisTemplate.opsForValue().set(KEY_PREFIX + instanceId, definitionId.toString());
        redisTemplate.opsForSet().add(KEY_ALL, instanceId.toString());
    }

    @Override
    public UUID get(UUID instanceId) {
        String value = redisTemplate.opsForValue().get(KEY_PREFIX + instanceId);
        return value != null ? UUID.fromString(value) : null;
    }

    @Override
    public void remove(UUID instanceId) {
        redisTemplate.delete(KEY_PREFIX + instanceId);
        redisTemplate.opsForSet().remove(KEY_ALL, instanceId.toString());
    }

    @Override
    public Set<UUID> allInstanceIds() {
        Set<String> members = redisTemplate.opsForSet().members(KEY_ALL);
        if (members == null || members.isEmpty()) {
            return Set.of();
        }
        return members.stream()
                .map(UUID::fromString)
                .collect(Collectors.toSet());
    }
}
