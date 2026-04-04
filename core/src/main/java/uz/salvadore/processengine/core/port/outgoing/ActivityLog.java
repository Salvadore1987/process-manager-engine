package uz.salvadore.processengine.core.port.outgoing;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public interface ActivityLog {

    void taskStarted(UUID processInstanceId, String nodeId, String topic, Instant startedAt);

    void taskCompleted(UUID processInstanceId, String nodeId, Map<String, Object> result, Instant completedAt);

    void taskFailed(UUID processInstanceId, String nodeId, String errorCode, String errorMessage, Instant occurredAt);

    void processCompleted(UUID processInstanceId, Instant completedAt);

    void processErrored(UUID processInstanceId, String errorCode, String errorMessage, Instant occurredAt);
}
