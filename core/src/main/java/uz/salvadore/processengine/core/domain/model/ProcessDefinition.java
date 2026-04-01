package uz.salvadore.processengine.core.domain.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import uz.salvadore.processengine.core.domain.enums.NodeType;
import uz.salvadore.processengine.core.util.UUIDv7;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ProcessDefinition {

    private final UUID id;
    private final String key;
    private final int version;
    private final String name;
    private final String bpmnXml;
    private final List<FlowNode> flowNodes;
    private final List<SequenceFlow> sequenceFlows;
    private final Instant deployedAt;

    private ProcessDefinition(UUID id, String key, int version, String name, String bpmnXml,
                              List<FlowNode> flowNodes, List<SequenceFlow> sequenceFlows, Instant deployedAt) {
        this.id = id;
        this.key = key;
        this.version = version;
        this.name = name;
        this.bpmnXml = bpmnXml;
        this.flowNodes = List.copyOf(flowNodes);
        this.sequenceFlows = List.copyOf(sequenceFlows);
        this.deployedAt = deployedAt;
    }

    public static ProcessDefinition create(String key, int version, String name, String bpmnXml,
                                           List<FlowNode> flowNodes, List<SequenceFlow> sequenceFlows) {
        validate(flowNodes);
        return new ProcessDefinition(UUIDv7.generate(), key, version, name, bpmnXml,
                flowNodes, sequenceFlows, Instant.now());
    }

    @JsonCreator
    public static ProcessDefinition restore(@JsonProperty("id") UUID id,
                                            @JsonProperty("key") String key,
                                            @JsonProperty("version") int version,
                                            @JsonProperty("name") String name,
                                            @JsonProperty("bpmnXml") String bpmnXml,
                                            @JsonProperty("flowNodes") List<FlowNode> flowNodes,
                                            @JsonProperty("sequenceFlows") List<SequenceFlow> sequenceFlows,
                                            @JsonProperty("deployedAt") Instant deployedAt) {
        return new ProcessDefinition(id, key, version, name, bpmnXml, flowNodes, sequenceFlows, deployedAt);
    }

    private static void validate(List<FlowNode> flowNodes) {
        long startEventCount = flowNodes.stream()
                .filter(node -> node.type() == NodeType.START_EVENT)
                .count();
        if (startEventCount != 1) {
            throw new IllegalArgumentException("Process definition must have exactly 1 StartEvent, found " + startEventCount);
        }

        long endEventCount = flowNodes.stream()
                .filter(node -> node.type() == NodeType.END_EVENT)
                .count();
        if (endEventCount < 1) {
            throw new IllegalArgumentException("Process definition must have at least 1 EndEvent");
        }
    }

    public UUID getId() {
        return id;
    }

    public String getKey() {
        return key;
    }

    public int getVersion() {
        return version;
    }

    public String getName() {
        return name;
    }

    public String getBpmnXml() {
        return bpmnXml;
    }

    public List<FlowNode> getFlowNodes() {
        return flowNodes;
    }

    public List<SequenceFlow> getSequenceFlows() {
        return sequenceFlows;
    }

    public Instant getDeployedAt() {
        return deployedAt;
    }

    public ProcessDefinition withVersion(int newVersion) {
        return new ProcessDefinition(this.id, this.key, newVersion, this.name, this.bpmnXml,
                this.flowNodes, this.sequenceFlows, this.deployedAt);
    }
}
