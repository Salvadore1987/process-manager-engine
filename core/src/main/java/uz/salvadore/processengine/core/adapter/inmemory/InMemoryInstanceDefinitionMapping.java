package uz.salvadore.processengine.core.adapter.inmemory;

import uz.salvadore.processengine.core.port.outgoing.InstanceDefinitionMapping;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory mapping from process instance IDs to their definition IDs.
 * Thread-safe for concurrent access.
 */
public final class InMemoryInstanceDefinitionMapping implements InstanceDefinitionMapping {

    private final ConcurrentHashMap<UUID, UUID> mapping = new ConcurrentHashMap<>();

    @Override
    public void put(UUID instanceId, UUID definitionId) {
        mapping.put(instanceId, definitionId);
    }

    @Override
    public UUID get(UUID instanceId) {
        return mapping.get(instanceId);
    }

    @Override
    public void remove(UUID instanceId) {
        mapping.remove(instanceId);
    }

    @Override
    public Set<UUID> allInstanceIds() {
        return Set.copyOf(mapping.keySet());
    }
}
