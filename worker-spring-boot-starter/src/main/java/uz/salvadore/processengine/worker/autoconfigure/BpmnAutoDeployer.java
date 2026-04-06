package uz.salvadore.processengine.worker.autoconfigure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import uz.salvadore.processengine.core.domain.model.CallActivity;
import uz.salvadore.processengine.core.domain.model.DeploymentBundle;
import uz.salvadore.processengine.core.domain.model.ProcessDefinition;
import uz.salvadore.processengine.core.engine.ProcessEngine;
import uz.salvadore.processengine.core.parser.BpmnParser;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class BpmnAutoDeployer implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(BpmnAutoDeployer.class);

    private final ProcessEngine processEngine;
    private final WorkerProperties properties;
    private final ResourcePatternResolver resourcePatternResolver;
    private final BpmnParser bpmnParser;
    private volatile boolean running;

    public BpmnAutoDeployer(ProcessEngine processEngine,
                            WorkerProperties properties,
                            ResourcePatternResolver resourcePatternResolver) {
        this.processEngine = processEngine;
        this.properties = properties;
        this.resourcePatternResolver = resourcePatternResolver;
        this.bpmnParser = new BpmnParser();
    }

    @Override
    public void start() {
        WorkerProperties.AutoDeployProperties autoDeployProps = properties.getAutoDeploy();
        if (!autoDeployProps.isEnabled()) {
            log.info("BPMN auto-deploy is disabled");
            return;
        }

        String resourceLocation = normalizeResourceLocation(autoDeployProps.getResourceLocation());
        log.info("Scanning for BPMN files in: {}", resourceLocation);

        try {
            Map<String, String> bpmnFiles = scanBpmnFiles(resourceLocation);
            if (bpmnFiles.isEmpty()) {
                log.info("No BPMN files found in {}", resourceLocation);
                running = true;
                return;
            }

            log.info("Found {} BPMN file(s)", bpmnFiles.size());
            deployAll(bpmnFiles, autoDeployProps.isFailOnError());

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
        String normalized = location.endsWith("/") ? location : location + "/";
        return normalized;
    }

    private Map<String, String> scanBpmnFiles(String resourceLocation) throws IOException {
        Resource[] resources = resourcePatternResolver.getResources(resourceLocation + "**/*.bpmn");
        Map<String, String> bpmnFiles = new LinkedHashMap<>();

        for (Resource resource : resources) {
            String filename = resource.getFilename();
            if (filename == null) {
                continue;
            }
            try (InputStream inputStream = resource.getInputStream()) {
                String content = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
                bpmnFiles.put(filename, content);
            }
        }

        return bpmnFiles;
    }

    private void deployAll(Map<String, String> bpmnFiles, boolean failOnError) {
        Map<String, ProcessDefinition> parsed = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : bpmnFiles.entrySet()) {
            List<ProcessDefinition> definitions = bpmnParser.parse(entry.getValue());
            parsed.put(entry.getKey(), definitions.getFirst());
        }

        Map<String, Set<String>> callGraph = buildCallGraph(parsed);
        Set<String> referencedAsSubprocess = collectAllCalledFileNames(callGraph);

        for (Map.Entry<String, ProcessDefinition> entry : parsed.entrySet()) {
            String filename = entry.getKey();
            ProcessDefinition definition = entry.getValue();
            Set<String> calledElements = callGraph.getOrDefault(filename, Set.of());

            if (!calledElements.isEmpty()) {
                deployAsBundle(filename, bpmnFiles, callGraph, failOnError);
            } else if (!referencedAsSubprocess.contains(filename)) {
                deployStandalone(definition, filename, failOnError);
            } else {
                log.debug("Skipping {} — will be deployed as part of a bundle", filename);
            }
        }
    }

    private Map<String, Set<String>> buildCallGraph(Map<String, ProcessDefinition> parsed) {
        Map<String, Set<String>> callGraph = new HashMap<>();

        for (Map.Entry<String, ProcessDefinition> entry : parsed.entrySet()) {
            String filename = entry.getKey();
            ProcessDefinition definition = entry.getValue();

            Set<String> calledFileNames = new HashSet<>();
            definition.getFlowNodes().stream()
                    .filter(CallActivity.class::isInstance)
                    .map(CallActivity.class::cast)
                    .map(CallActivity::calledElement)
                    .forEach(calledElement -> calledFileNames.add(calledElement + ".bpmn"));

            callGraph.put(filename, calledFileNames);
        }

        return callGraph;
    }

    private Set<String> collectAllCalledFileNames(Map<String, Set<String>> callGraph) {
        Set<String> allCalled = new HashSet<>();
        for (Set<String> calledFiles : callGraph.values()) {
            allCalled.addAll(calledFiles);
        }
        return allCalled;
    }

    private void deployAsBundle(String mainFileName,
                                Map<String, String> allFiles,
                                Map<String, Set<String>> callGraph,
                                boolean failOnError) {
        try {
            LinkedHashMap<String, String> bundleFiles = new LinkedHashMap<>();
            bundleFiles.put(mainFileName, allFiles.get(mainFileName));
            collectSubprocessFiles(mainFileName, allFiles, callGraph, bundleFiles, new HashSet<>());

            DeploymentBundle bundle = new DeploymentBundle(bundleFiles);
            processEngine.deployBundle(bundle);
            log.info("Deployed bundle: {} ({} file(s))", mainFileName, bundleFiles.size());

        } catch (Exception e) {
            handleError("Failed to deploy bundle: " + mainFileName, e, failOnError);
        }
    }

    private void collectSubprocessFiles(String currentFileName,
                                        Map<String, String> allFiles,
                                        Map<String, Set<String>> callGraph,
                                        LinkedHashMap<String, String> bundleFiles,
                                        Set<String> visited) {
        Set<String> calledFiles = callGraph.getOrDefault(currentFileName, Set.of());
        for (String calledFileName : calledFiles) {
            if (visited.contains(calledFileName)) {
                continue;
            }
            visited.add(calledFileName);

            String content = allFiles.get(calledFileName);
            if (content != null && !bundleFiles.containsKey(calledFileName)) {
                bundleFiles.put(calledFileName, content);
                collectSubprocessFiles(calledFileName, allFiles, callGraph, bundleFiles, visited);
            }
        }
    }

    private void deployStandalone(ProcessDefinition definition, String filename, boolean failOnError) {
        try {
            processEngine.deploy(definition);
            log.info("Deployed standalone process: {}", filename);
        } catch (Exception e) {
            handleError("Failed to deploy process: " + filename, e, failOnError);
        }
    }

    private void handleError(String message, Exception cause, boolean failOnError) {
        if (failOnError) {
            throw new IllegalStateException(message, cause);
        }
        log.error("{}: {}", message, cause.getMessage(), cause);
    }
}
