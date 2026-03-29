package uz.salvadore.processengine.core.port.outgoing;

import java.time.Duration;
import java.util.UUID;
import java.util.function.Consumer;

public interface TimerService {

    void schedule(UUID processInstanceId, UUID tokenId, String nodeId, Duration duration,
                  Consumer<TimerCallback> callback);

    void cancel(UUID processInstanceId, UUID tokenId);

    record TimerCallback(UUID processInstanceId, UUID tokenId, String nodeId) {
    }
}
