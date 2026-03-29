package uz.salvadore.processengine.core.engine.eventsourcing;

import uz.salvadore.processengine.core.domain.event.ProcessEvent;
import uz.salvadore.processengine.core.domain.model.ProcessInstance;

import java.util.List;

/**
 * Reconstructs a ProcessInstance by folding (replaying) a sequence of ProcessEvents
 * through the EventApplier. The first event must be a ProcessStartedEvent.
 */
public final class ProcessInstanceProjection {

    private final EventApplier eventApplier;

    public ProcessInstanceProjection(EventApplier eventApplier) {
        this.eventApplier = eventApplier;
    }

    public ProcessInstance replay(List<ProcessEvent> events) {
        if (events.isEmpty()) {
            throw new IllegalArgumentException("Cannot replay empty event list");
        }

        ProcessInstance instance = null;
        for (ProcessEvent event : events) {
            instance = eventApplier.apply(event, instance);
        }
        return instance;
    }
}
