package uz.salvadore.processengine.core.domain.model;

import uz.salvadore.processengine.core.domain.enums.NodeType;

import java.util.List;

public record ParallelGateway(
        String id,
        String name,
        List<String> incomingFlows,
        List<String> outgoingFlows
) implements FlowNode {

    @Override
    public NodeType type() {
        return NodeType.PARALLEL_GATEWAY;
    }
}
