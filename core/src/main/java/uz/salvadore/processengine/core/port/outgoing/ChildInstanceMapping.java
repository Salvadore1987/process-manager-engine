package uz.salvadore.processengine.core.port.outgoing;

import java.util.List;
import java.util.UUID;

public interface ChildInstanceMapping {

    void put(UUID childInstanceId, UUID parentInstanceId);

    UUID getParent(UUID childInstanceId);

    List<UUID> getChildren(UUID parentInstanceId);

    void remove(UUID childInstanceId);
}
