package uz.salvadore.processengine.redis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import uz.salvadore.processengine.core.domain.exception.DuplicateProcessDefinitionException;
import uz.salvadore.processengine.core.domain.model.ProcessDefinition;
import uz.salvadore.processengine.core.port.outgoing.ProcessDefinitionStore;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Redis-backed implementation of ProcessDefinitionStore.
 * Uses a Lua script for atomic deploy with version check and duplicate detection.
 */
public final class RedisProcessDefinitionStore implements ProcessDefinitionStore {

    private static final String KEY_PREFIX_ID = "pe:def:id:";
    private static final String KEY_PREFIX_KEY = "pe:def:key:";
    private static final String KEY_ALL = "pe:def:all";

    private static final String DEPLOY_LUA_SCRIPT = """
            local keyList = KEYS[1]
            local keyId = KEYS[2]
            local keyAll = KEYS[3]
            local definitionJson = ARGV[1]
            local definitionId = ARGV[2]
            local bpmnXml = ARGV[3]

            -- Get the latest version's JSON to check for duplicate BPMN
            local listLen = redis.call('LLEN', keyList)
            if listLen > 0 then
                local latestJson = redis.call('LINDEX', keyList, listLen - 1)
                local latestBpmn = cjson.decode(latestJson)['bpmnXml']
                if latestBpmn == bpmnXml then
                    return 'DUPLICATE'
                end
            end

            -- Store the definition
            redis.call('RPUSH', keyList, definitionJson)
            redis.call('SET', keyId, definitionJson)
            redis.call('SADD', keyAll, definitionId)

            return 'OK'
            """;

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final DefaultRedisScript<String> deployScript;

    public RedisProcessDefinitionStore(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.deployScript = new DefaultRedisScript<>(DEPLOY_LUA_SCRIPT, String.class);
    }

    @Override
    public ProcessDefinition deploy(ProcessDefinition definition) {
        String keyList = KEY_PREFIX_KEY + definition.getKey();
        String keyAll = KEY_ALL;

        // Determine next version
        Long listLen = redisTemplate.opsForList().size(keyList);
        int nextVersion = (listLen != null && listLen > 0) ? getNextVersion(keyList, listLen) : 1;

        ProcessDefinition versioned = definition.withVersion(nextVersion);
        String json = serialize(versioned);
        String keyId = KEY_PREFIX_ID + versioned.getId();

        String result = redisTemplate.execute(
                deployScript,
                List.of(keyList, keyId, keyAll),
                json,
                versioned.getId().toString(),
                definition.getBpmnXml()
        );

        if ("DUPLICATE".equals(result)) {
            int latestVersion = nextVersion - 1;
            throw new DuplicateProcessDefinitionException(definition.getKey(), latestVersion);
        }

        return versioned;
    }

    @Override
    public void undeploy(String key) {
        String keyList = KEY_PREFIX_KEY + key;
        List<String> jsonList = redisTemplate.opsForList().range(keyList, 0, -1);
        if (jsonList != null) {
            for (String json : jsonList) {
                ProcessDefinition def = deserialize(json);
                redisTemplate.delete(KEY_PREFIX_ID + def.getId());
                redisTemplate.opsForSet().remove(KEY_ALL, def.getId().toString());
            }
        }
        redisTemplate.delete(keyList);
    }

    @Override
    public Optional<ProcessDefinition> getById(UUID id) {
        String json = redisTemplate.opsForValue().get(KEY_PREFIX_ID + id);
        if (json == null) {
            return Optional.empty();
        }
        return Optional.of(deserialize(json));
    }

    @Override
    public Optional<ProcessDefinition> getByKey(String key) {
        String keyList = KEY_PREFIX_KEY + key;
        Long size = redisTemplate.opsForList().size(keyList);
        if (size == null || size == 0) {
            return Optional.empty();
        }
        String json = redisTemplate.opsForList().index(keyList, size - 1);
        if (json == null) {
            return Optional.empty();
        }
        return Optional.of(deserialize(json));
    }

    @Override
    public List<ProcessDefinition> getVersions(String key) {
        String keyList = KEY_PREFIX_KEY + key;
        List<String> jsonList = redisTemplate.opsForList().range(keyList, 0, -1);
        if (jsonList == null || jsonList.isEmpty()) {
            return List.of();
        }
        return jsonList.stream()
                .map(this::deserialize)
                .toList();
    }

    @Override
    public List<ProcessDefinition> list() {
        Set<String> ids = redisTemplate.opsForSet().members(KEY_ALL);
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<ProcessDefinition> result = new ArrayList<>();
        for (String id : ids) {
            String json = redisTemplate.opsForValue().get(KEY_PREFIX_ID + id);
            if (json != null) {
                result.add(deserialize(json));
            }
        }
        return result;
    }

    @Override
    public int size() {
        Long count = redisTemplate.opsForSet().size(KEY_ALL);
        return count != null ? count.intValue() : 0;
    }

    private int getNextVersion(String keyList, long listLen) {
        String latestJson = redisTemplate.opsForList().index(keyList, listLen - 1);
        if (latestJson == null) {
            return 1;
        }
        ProcessDefinition latest = deserialize(latestJson);
        return latest.getVersion() + 1;
    }

    private String serialize(ProcessDefinition definition) {
        try {
            return objectMapper.writeValueAsString(definition);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize ProcessDefinition: " + definition.getId(), e);
        }
    }

    private ProcessDefinition deserialize(String json) {
        try {
            return objectMapper.readValue(json, ProcessDefinition.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to deserialize ProcessDefinition from JSON", e);
        }
    }
}
