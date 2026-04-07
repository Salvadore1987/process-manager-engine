package uz.salvadore.processengine.core.domain.model;

import uz.salvadore.processengine.core.domain.enums.NodeType;

import java.util.List;

public record ExclusiveGateway(
        String id,
        String name,
        List<String> incomingFlows,
        List<String> outgoingFlows,
        String defaultFlowId
) implements FlowNode {

    @Override
    public NodeType type() {
        return NodeType.EXCLUSIVE_GATEWAY;
    }
}
