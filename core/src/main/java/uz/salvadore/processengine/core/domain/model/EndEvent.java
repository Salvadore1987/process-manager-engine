package uz.salvadore.processengine.core.domain.model;

import uz.salvadore.processengine.core.domain.enums.NodeType;

import java.util.List;

public record EndEvent(
        String id,
        String name,
        List<String> incomingFlows,
        List<String> outgoingFlows,
        String errorCode
) implements FlowNode {

    @Override
    public NodeType type() {
        return NodeType.END_EVENT;
    }
}
