package uz.salvadore.processengine.core.adapter.inmemory;

import uz.salvadore.processengine.core.port.outgoing.SequenceGenerator;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Assigns monotonically increasing sequence numbers per processInstanceId.
 * Thread-safe for concurrent access across multiple process instances.
 */
public final class InMemorySequenceGenerator implements SequenceGenerator {

    private final ConcurrentHashMap<UUID, AtomicLong> counters = new ConcurrentHashMap<>();

    @Override
    public long next(UUID processInstanceId) {
        return counters
                .computeIfAbsent(processInstanceId, id -> new AtomicLong(0))
                .incrementAndGet();
    }

    @Override
    public long current(UUID processInstanceId) {
        AtomicLong counter = counters.get(processInstanceId);
        return counter == null ? 0 : counter.get();
    }

    @Override
    public void reset(UUID processInstanceId) {
        counters.remove(processInstanceId);
    }
}
