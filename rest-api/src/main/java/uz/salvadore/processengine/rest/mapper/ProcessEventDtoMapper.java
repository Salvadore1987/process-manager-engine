package uz.salvadore.processengine.rest.mapper;

import org.springframework.stereotype.Component;
import uz.salvadore.processengine.core.domain.event.ProcessEvent;
import uz.salvadore.processengine.rest.dto.ProcessEventDto;

@Component
public class ProcessEventDtoMapper {

    public ProcessEventDto toDto(ProcessEvent event) {
        return new ProcessEventDto(
                event.id(),
                event.processInstanceId(),
                event.getClass().getSimpleName(),
                event.occurredAt(),
                event.sequenceNumber()
        );
    }
}
