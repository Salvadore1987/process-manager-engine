package uz.salvadore.processengine.redis;

import org.springframework.data.redis.core.StringRedisTemplate;
import uz.salvadore.processengine.core.port.outgoing.BusinessKeyMapping;

import java.util.UUID;

public final class RedisBusinessKeyMapping implements BusinessKeyMapping {

    private static final String KEY_PREFIX = "pe:biz-key:";

    private final StringRedisTemplate redisTemplate;

    public RedisBusinessKeyMapping(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void put(String businessKey, UUID instanceId) {
        redisTemplate.opsForValue().set(KEY_PREFIX + businessKey, instanceId.toString());
    }

    @Override
    public UUID get(String businessKey) {
        String value = redisTemplate.opsForValue().get(KEY_PREFIX + businessKey);
        return value != null ? UUID.fromString(value) : null;
    }

    @Override
    public void remove(String businessKey) {
        redisTemplate.delete(KEY_PREFIX + businessKey);
    }
}
