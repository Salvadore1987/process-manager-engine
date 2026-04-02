package uz.salvadore.processengine.redis;

import org.springframework.data.redis.core.StringRedisTemplate;
import uz.salvadore.processengine.core.port.outgoing.SequenceGenerator;

import java.util.UUID;

/**
 * Redis-backed implementation of SequenceGenerator.
 * Uses Redis INCR for atomic sequence number generation per process instance.
 */
public final class RedisSequenceGenerator implements SequenceGenerator {

    private static final String KEY_PREFIX = "pe:seq:";

    private final StringRedisTemplate redisTemplate;

    public RedisSequenceGenerator(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public long next(UUID processInstanceId) {
        Long value = redisTemplate.opsForValue().increment(KEY_PREFIX + processInstanceId);
        if (value == null) {
            throw new IllegalStateException("Redis INCR returned null for key: " + KEY_PREFIX + processInstanceId);
        }
        return value;
    }

    @Override
    public long current(UUID processInstanceId) {
        String value = redisTemplate.opsForValue().get(KEY_PREFIX + processInstanceId);
        return value == null ? 0 : Long.parseLong(value);
    }

    @Override
    public void reset(UUID processInstanceId) {
        redisTemplate.delete(KEY_PREFIX + processInstanceId);
    }
}
