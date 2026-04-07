package uz.salvadore.processengine.worker.autoconfigure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;

public class BpmnAutoDeployer implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(BpmnAutoDeployer.class);

    private final ProcessEngineClient engineClient;
    private final WorkerProperties properties;
    private final ResourcePatternResolver resourcePatternResolver;
    private volatile boolean running;

    public BpmnAutoDeployer(ProcessEngineClient engineClient,
                            WorkerProperties properties,
                            ResourcePatternResolver resourcePatternResolver) {
        this.engineClient = engineClient;
        this.properties = properties;
        this.resourcePatternResolver = resourcePatternResolver;
    }

    @Override
    public void start() {
        WorkerProperties.AutoDeployProperties autoDeployProps = properties.getAutoDeploy();
        if (!autoDeployProps.isEnabled()) {
            log.info("BPMN auto-deploy is disabled");
            running = true;
            return;
        }

        String resourceLocation = normalizeResourceLocation(autoDeployProps.getResourceLocation());
        log.info("Scanning for BPMN files in: {}", resourceLocation);

        try {
            Map<String, byte[]> bpmnFiles = scanBpmnFiles(resourceLocation);
            if (bpmnFiles.isEmpty()) {
                log.info("No BPMN files found in {}", resourceLocation);
                running = true;
                return;
            }

            log.info("Found {} BPMN file(s), deploying to engine at {}", bpmnFiles.size(), properties.getEngineUrl());
            deploy(bpmnFiles, autoDeployProps.isFailOnError());

        } catch (IOException e) {
            handleError("Failed to scan BPMN resources from " + resourceLocation, e,
                    autoDeployProps.isFailOnError());
        }

        running = true;
    }

    @Override
    public void stop() {
        running = false;
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public int getPhase() {
        return Integer.MIN_VALUE + 300;
    }

    private String normalizeResourceLocation(String location) {
        return location.endsWith("/") ? location : location + "/";
    }

    private Map<String, byte[]> scanBpmnFiles(String resourceLocation) throws IOException {
        Resource[] resources = resourcePatternResolver.getResources(resourceLocation + "**/*.bpmn");
        Map<String, byte[]> bpmnFiles = new LinkedHashMap<>();

        for (Resource resource : resources) {
            String filename = resource.getFilename();
            if (filename == null) {
                continue;
            }
            try (InputStream inputStream = resource.getInputStream()) {
                bpmnFiles.put(filename, inputStream.readAllBytes());
            }
        }

        return bpmnFiles;
    }

    private void deploy(Map<String, byte[]> bpmnFiles, boolean failOnError) {
        try {
            if (bpmnFiles.size() == 1) {
                Map.Entry<String, byte[]> entry = bpmnFiles.entrySet().iterator().next();
                engineClient.deployDefinition(entry.getKey(), entry.getValue());
            } else {
                engineClient.deployBundle(bpmnFiles);
            }
        } catch (Exception e) {
            handleError("Failed to deploy BPMN files to engine", e, failOnError);
        }
    }

    private void handleError(String message, Exception cause, boolean failOnError) {
        if (failOnError) {
            throw new IllegalStateException(message, cause);
        }
        log.error("{}: {}", message, cause.getMessage(), cause);
    }
}
