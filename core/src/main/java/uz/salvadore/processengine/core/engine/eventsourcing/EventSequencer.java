package uz.salvadore.processengine.core.engine.eventsourcing;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Assigns monotonically increasing sequence numbers per processInstanceId.
 * Thread-safe for concurrent access across multiple process instances.
 */
public final class EventSequencer {

    private final ConcurrentHashMap<UUID, AtomicLong> counters = new ConcurrentHashMap<>();

    public long next(UUID processInstanceId) {
        return counters
                .computeIfAbsent(processInstanceId, id -> new AtomicLong(0))
                .incrementAndGet();
    }

    public long current(UUID processInstanceId) {
        AtomicLong counter = counters.get(processInstanceId);
        return counter == null ? 0 : counter.get();
    }

    public void reset(UUID processInstanceId) {
        counters.remove(processInstanceId);
    }
}
