package uz.salvadore.processengine.core.port.outgoing;

import java.util.UUID;

public interface BusinessKeyMapping {

    void put(String businessKey, UUID instanceId);

    UUID get(String businessKey);

    void remove(String businessKey);
}
