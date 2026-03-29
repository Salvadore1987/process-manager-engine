package uz.salvadore.processengine.core.adapter.noop;

import uz.salvadore.processengine.core.domain.event.ProcessEvent;
import uz.salvadore.processengine.core.port.outgoing.ProcessEventStore;

import java.util.List;
import java.util.UUID;

/**
 * No-op implementation of ProcessEventStore. Discards all events and returns empty lists.
 * Useful when persistence is disabled.
 */
public final class NoOpEventStore implements ProcessEventStore {

    @Override
    public void append(ProcessEvent event) {
        // no-op
    }

    @Override
    public void appendAll(List<ProcessEvent> events) {
        // no-op
    }

    @Override
    public List<ProcessEvent> getEvents(UUID processInstanceId) {
        return List.of();
    }
}
