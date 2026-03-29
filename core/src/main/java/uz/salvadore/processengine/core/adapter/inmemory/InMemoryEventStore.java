package uz.salvadore.processengine.core.adapter.inmemory;

import uz.salvadore.processengine.core.domain.event.ProcessEvent;
import uz.salvadore.processengine.core.port.outgoing.ProcessEventStore;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Thread-safe in-memory implementation of ProcessEventStore.
 * Intended for testing and development use.
 */
public final class InMemoryEventStore implements ProcessEventStore {

    private final ConcurrentHashMap<UUID, CopyOnWriteArrayList<ProcessEvent>> store =
            new ConcurrentHashMap<>();

    @Override
    public void append(ProcessEvent event) {
        store.computeIfAbsent(event.processInstanceId(), id -> new CopyOnWriteArrayList<>())
                .add(event);
    }

    @Override
    public void appendAll(List<ProcessEvent> events) {
        for (ProcessEvent event : events) {
            append(event);
        }
    }

    @Override
    public List<ProcessEvent> getEvents(UUID processInstanceId) {
        CopyOnWriteArrayList<ProcessEvent> events = store.get(processInstanceId);
        if (events == null) {
            return List.of();
        }
        return events.stream()
                .sorted(Comparator.comparingLong(ProcessEvent::sequenceNumber))
                .toList();
    }

    public int size() {
        return store.values().stream()
                .mapToInt(List::size)
                .sum();
    }

    public void clear() {
        store.clear();
    }
}
