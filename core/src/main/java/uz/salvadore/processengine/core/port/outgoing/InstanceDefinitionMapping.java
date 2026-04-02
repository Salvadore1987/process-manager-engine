package uz.salvadore.processengine.core.port.outgoing;

import java.util.Set;
import java.util.UUID;

public interface InstanceDefinitionMapping {

    void put(UUID instanceId, UUID definitionId);

    UUID get(UUID instanceId);

    void remove(UUID instanceId);

    Set<UUID> allInstanceIds();
}
