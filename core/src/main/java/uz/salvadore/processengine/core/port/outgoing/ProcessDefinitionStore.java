package uz.salvadore.processengine.core.port.outgoing;

import uz.salvadore.processengine.core.domain.model.ProcessDefinition;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProcessDefinitionStore {

    ProcessDefinition deploy(ProcessDefinition definition);

    void undeploy(String key);

    Optional<ProcessDefinition> getById(UUID id);

    Optional<ProcessDefinition> getByKey(String key);

    List<ProcessDefinition> getVersions(String key);

    List<ProcessDefinition> list();

    int size();
}
