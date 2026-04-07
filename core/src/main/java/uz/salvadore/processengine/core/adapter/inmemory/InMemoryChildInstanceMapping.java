package uz.salvadore.processengine.core.adapter.inmemory;

import uz.salvadore.processengine.core.port.outgoing.ChildInstanceMapping;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryChildInstanceMapping implements ChildInstanceMapping {

    private final ConcurrentHashMap<UUID, UUID> childToParent = new ConcurrentHashMap<>();

    @Override
    public void put(UUID childInstanceId, UUID parentInstanceId) {
        childToParent.put(childInstanceId, parentInstanceId);
    }

    @Override
    public UUID getParent(UUID childInstanceId) {
        return childToParent.get(childInstanceId);
    }

    @Override
    public List<UUID> getChildren(UUID parentInstanceId) {
        return childToParent.entrySet().stream()
                .filter(entry -> entry.getValue().equals(parentInstanceId))
                .map(java.util.Map.Entry::getKey)
                .toList();
    }

    @Override
    public void remove(UUID childInstanceId) {
        childToParent.remove(childInstanceId);
    }
}
