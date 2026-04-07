package uz.salvadore.processengine.redis;

import org.springframework.data.redis.core.StringRedisTemplate;
import uz.salvadore.processengine.core.port.outgoing.ChildInstanceMapping;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class RedisChildInstanceMapping implements ChildInstanceMapping {

    private static final String CHILD_TO_PARENT_PREFIX = "pe:child-parent:";
    private static final String PARENT_CHILDREN_PREFIX = "pe:parent-children:";

    private final StringRedisTemplate redisTemplate;

    public RedisChildInstanceMapping(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void put(UUID childInstanceId, UUID parentInstanceId) {
        redisTemplate.opsForValue().set(
                CHILD_TO_PARENT_PREFIX + childInstanceId, parentInstanceId.toString());
        redisTemplate.opsForSet().add(
                PARENT_CHILDREN_PREFIX + parentInstanceId, childInstanceId.toString());
    }

    @Override
    public UUID getParent(UUID childInstanceId) {
        String value = redisTemplate.opsForValue().get(CHILD_TO_PARENT_PREFIX + childInstanceId);
        return value != null ? UUID.fromString(value) : null;
    }

    @Override
    public List<UUID> getChildren(UUID parentInstanceId) {
        Set<String> members = redisTemplate.opsForSet().members(
                PARENT_CHILDREN_PREFIX + parentInstanceId);
        if (members == null || members.isEmpty()) {
            return List.of();
        }
        return members.stream()
                .map(UUID::fromString)
                .toList();
    }

    @Override
    public void remove(UUID childInstanceId) {
        UUID parentInstanceId = getParent(childInstanceId);
        redisTemplate.delete(CHILD_TO_PARENT_PREFIX + childInstanceId);
        if (parentInstanceId != null) {
            redisTemplate.opsForSet().remove(
                    PARENT_CHILDREN_PREFIX + parentInstanceId, childInstanceId.toString());
        }
    }
}
