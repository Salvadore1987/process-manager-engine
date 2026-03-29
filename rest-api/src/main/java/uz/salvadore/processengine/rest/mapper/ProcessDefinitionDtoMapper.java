package uz.salvadore.processengine.rest.mapper;

import org.springframework.stereotype.Component;
import uz.salvadore.processengine.core.domain.model.ProcessDefinition;
import uz.salvadore.processengine.rest.dto.ProcessDefinitionDto;

@Component
public class ProcessDefinitionDtoMapper {

    public ProcessDefinitionDto toDto(ProcessDefinition definition) {
        return new ProcessDefinitionDto(
                definition.getId(),
                definition.getKey(),
                definition.getVersion(),
                definition.getName(),
                definition.getDeployedAt()
        );
    }
}
