package uz.salvadore.processengine.core.engine;

import uz.salvadore.processengine.core.domain.exception.DuplicateProcessDefinitionException;
import uz.salvadore.processengine.core.domain.model.ProcessDefinition;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory registry for deployed process definitions.
 * Supports versioning: multiple versions per key, lookup returns latest.
 */
public final class ProcessDefinitionRepository {

    private final ConcurrentHashMap<UUID, ProcessDefinition> byId = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, List<ProcessDefinition>> byKey = new ConcurrentHashMap<>();

    public ProcessDefinition deploy(ProcessDefinition definition) {
        ProcessDefinition[] result = new ProcessDefinition[1];

        byKey.compute(definition.getKey(), (key, existing) -> {
            List<ProcessDefinition> versions = existing != null ? new ArrayList<>(existing) : new ArrayList<>();

            int nextVersion;
            if (!versions.isEmpty()) {
                ProcessDefinition latest = versions.stream()
                        .max(Comparator.comparingInt(ProcessDefinition::getVersion))
                        .orElseThrow();

                if (latest.getBpmnXml().equals(definition.getBpmnXml())) {
                    throw new DuplicateProcessDefinitionException(key, latest.getVersion());
                }
                nextVersion = latest.getVersion() + 1;
            } else {
                nextVersion = 1;
            }

            ProcessDefinition versioned = definition.withVersion(nextVersion);
            versions.add(versioned);
            result[0] = versioned;
            return versions;
        });

        byId.put(result[0].getId(), result[0]);
        return result[0];
    }

    public void undeploy(String key) {
        List<ProcessDefinition> removed = byKey.remove(key);
        if (removed != null) {
            for (ProcessDefinition definition : removed) {
                byId.remove(definition.getId());
            }
        }
    }

    public Optional<ProcessDefinition> getById(UUID id) {
        return Optional.ofNullable(byId.get(id));
    }

    public Optional<ProcessDefinition> getByKey(String key) {
        List<ProcessDefinition> versions = byKey.get(key);
        if (versions == null || versions.isEmpty()) {
            return Optional.empty();
        }
        return versions.stream()
                .max(Comparator.comparingInt(ProcessDefinition::getVersion));
    }

    public List<ProcessDefinition> getVersions(String key) {
        List<ProcessDefinition> versions = byKey.get(key);
        if (versions == null) {
            return List.of();
        }
        return List.copyOf(versions);
    }

    public List<ProcessDefinition> list() {
        return List.copyOf(byId.values());
    }

    public int size() {
        return byId.size();
    }
}
