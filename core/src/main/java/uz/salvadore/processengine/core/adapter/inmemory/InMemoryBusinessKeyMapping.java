package uz.salvadore.processengine.core.adapter.inmemory;

import uz.salvadore.processengine.core.port.outgoing.BusinessKeyMapping;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryBusinessKeyMapping implements BusinessKeyMapping {

    private final ConcurrentHashMap<String, UUID> mapping = new ConcurrentHashMap<>();

    @Override
    public void put(String businessKey, UUID instanceId) {
        mapping.put(businessKey, instanceId);
    }

    @Override
    public UUID get(String businessKey) {
        return mapping.get(businessKey);
    }

    @Override
    public void remove(String businessKey) {
        mapping.remove(businessKey);
    }
}
