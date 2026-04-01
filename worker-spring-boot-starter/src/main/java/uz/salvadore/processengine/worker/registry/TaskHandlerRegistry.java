package uz.salvadore.processengine.worker.registry;

import uz.salvadore.processengine.worker.ExternalTaskHandler;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Maintains the mapping between task topics and their handler instances.
 * Each topic may have exactly one handler.
 */
public class TaskHandlerRegistry {

    private final Map<String, ExternalTaskHandler> handlers = new ConcurrentHashMap<>();
    private final Map<String, WorkerRetryConfig> retryConfigs = new ConcurrentHashMap<>();

    public void register(String topic, ExternalTaskHandler handler, WorkerRetryConfig retryConfig) {
        ExternalTaskHandler existing = handlers.putIfAbsent(topic, handler);
        if (existing != null) {
            throw new IllegalStateException(
                    "Duplicate @TaskHandler for topic '" + topic + "': " +
                            existing.getClass().getName() + " and " + handler.getClass().getName()
            );
        }
        retryConfigs.put(topic, retryConfig);
    }

    public ExternalTaskHandler getHandler(String topic) {
        return handlers.get(topic);
    }

    public WorkerRetryConfig getRetryConfig(String topic) {
        return retryConfigs.getOrDefault(topic, WorkerRetryConfig.DISABLED);
    }

    public Set<String> getTopics() {
        return Collections.unmodifiableSet(handlers.keySet());
    }
}
