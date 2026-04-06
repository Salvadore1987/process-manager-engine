package uz.salvadore.processengine.core.domain.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class DeploymentBundle {

    private final Map<String, String> bpmnFiles;
    private final String mainProcessFileName;

    public DeploymentBundle(Map<String, String> bpmnFiles) {
        if (bpmnFiles == null || bpmnFiles.isEmpty()) {
            throw new IllegalArgumentException("Deployment bundle must contain at least one BPMN file");
        }
        LinkedHashMap<String, String> ordered = new LinkedHashMap<>(bpmnFiles);
        this.bpmnFiles = Collections.unmodifiableMap(ordered);
        this.mainProcessFileName = ordered.keySet().iterator().next();
    }

    public String getMainProcess() {
        return mainProcessFileName;
    }

    public Map<String, String> getSubprocesses() {
        Map<String, String> subprocesses = new LinkedHashMap<>(bpmnFiles);
        subprocesses.remove(mainProcessFileName);
        return Collections.unmodifiableMap(subprocesses);
    }

    public boolean containsFile(String fileName) {
        return bpmnFiles.containsKey(fileName);
    }

    public Map<String, String> getBpmnFiles() {
        return bpmnFiles;
    }
}
