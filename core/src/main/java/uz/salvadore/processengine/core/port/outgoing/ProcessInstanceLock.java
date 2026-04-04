package uz.salvadore.processengine.core.port.outgoing;

import java.util.UUID;

public interface ProcessInstanceLock {

    void lock(UUID processInstanceId);

    void unlock(UUID processInstanceId);
}
