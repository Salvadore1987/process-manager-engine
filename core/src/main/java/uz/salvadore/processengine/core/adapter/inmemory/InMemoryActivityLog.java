package uz.salvadore.processengine.core.adapter.inmemory;

import uz.salvadore.processengine.core.port.outgoing.ActivityLog;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * No-op in-memory implementation of ActivityLog for testing.
 */
public final class InMemoryActivityLog implements ActivityLog {

    @Override
    public void taskStarted(UUID processInstanceId, String nodeId, String topic, Instant startedAt) {
        // no-op
    }

    @Override
    public void taskCompleted(UUID processInstanceId, String nodeId, Map<String, Object> result, Instant completedAt) {
        // no-op
    }

    @Override
    public void taskFailed(UUID processInstanceId, String nodeId, String errorCode, String errorMessage, Instant occurredAt) {
        // no-op
    }

    @Override
    public void processCompleted(UUID processInstanceId, Instant completedAt) {
        // no-op
    }

    @Override
    public void processErrored(UUID processInstanceId, String errorCode, String errorMessage, Instant occurredAt) {
        // no-op
    }
}
