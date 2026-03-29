package uz.salvadore.processengine.core.port.outgoing;

import uz.salvadore.processengine.core.domain.event.ProcessEvent;

import java.util.List;
import java.util.UUID;

public interface ProcessEventStore {

    void append(ProcessEvent event);

    void appendAll(List<ProcessEvent> events);

    List<ProcessEvent> getEvents(UUID processInstanceId);
}
