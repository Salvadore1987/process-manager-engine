package uz.salvadore.processengine.core.port.outgoing;

import java.util.UUID;

public interface SequenceGenerator {

    long next(UUID processInstanceId);

    long current(UUID processInstanceId);

    void reset(UUID processInstanceId);
}
