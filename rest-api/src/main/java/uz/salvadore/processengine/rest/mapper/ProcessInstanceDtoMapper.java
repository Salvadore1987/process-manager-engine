package uz.salvadore.processengine.rest.mapper;

import org.springframework.stereotype.Component;
import uz.salvadore.processengine.core.domain.model.ProcessInstance;
import uz.salvadore.processengine.rest.dto.ProcessInstanceDto;

@Component
public class ProcessInstanceDtoMapper {

    public ProcessInstanceDto toDto(ProcessInstance instance) {
        return new ProcessInstanceDto(
                instance.getId(),
                instance.getDefinitionId(),
                instance.getParentProcessInstanceId(),
                instance.getState().name(),
                instance.getVariables(),
                instance.getStartedAt(),
                instance.getCompletedAt()
        );
    }
}
