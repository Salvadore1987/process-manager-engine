package uz.salvadore.processengine.redis;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import uz.salvadore.processengine.core.port.outgoing.ProcessInstanceLock;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Redis-backed distributed lock per process instance.
 * Uses SET NX PX for acquisition and a Lua script for safe release.
 * Supports multiple concurrent locks per thread (different process instances).
 */
public final class RedisProcessInstanceLock implements ProcessInstanceLock {

    private static final Logger log = LoggerFactory.getLogger(RedisProcessInstanceLock.class);

    private static final String KEY_PREFIX = "pe:lock:";
    private static final Duration LOCK_TTL = Duration.ofSeconds(30);
    private static final long RETRY_INTERVAL_MS = 50;
    private static final int MAX_RETRIES = 600; // 30 seconds total

    private static final String UNLOCK_LUA_SCRIPT = """
            if redis.call('GET', KEYS[1]) == ARGV[1] then
                return redis.call('DEL', KEYS[1])
            else
                return 0
            end
            """;

    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<Long> unlockScript;
    private final ThreadLocal<Map<UUID, String>> lockValues = ThreadLocal.withInitial(HashMap::new);

    public RedisProcessInstanceLock(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.unlockScript = new DefaultRedisScript<>(UNLOCK_LUA_SCRIPT, Long.class);
    }

    @Override
    public void lock(UUID processInstanceId) {
        String key = KEY_PREFIX + processInstanceId;
        String value = UUID.randomUUID().toString();

        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            Boolean acquired = redisTemplate.opsForValue()
                    .setIfAbsent(key, value, LOCK_TTL);
            if (Boolean.TRUE.equals(acquired)) {
                lockValues.get().put(processInstanceId, value);
                return;
            }
            try {
                Thread.sleep(RETRY_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting for lock: " + processInstanceId, e);
            }
        }
        throw new IllegalStateException("Failed to acquire lock for process instance: " + processInstanceId
                + " after " + MAX_RETRIES + " retries");
    }

    @Override
    public void unlock(UUID processInstanceId) {
        String key = KEY_PREFIX + processInstanceId;
        Map<UUID, String> values = lockValues.get();
        String value = values.remove(processInstanceId);
        if (value == null) {
            log.warn("No lock value found for process instance: {}", processInstanceId);
            return;
        }
        redisTemplate.execute(unlockScript, List.of(key), value);
    }
}
