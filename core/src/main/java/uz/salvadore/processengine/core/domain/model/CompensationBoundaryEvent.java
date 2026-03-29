package uz.salvadore.processengine.core.domain.model;

import uz.salvadore.processengine.core.domain.enums.NodeType;

import java.util.List;

public record CompensationBoundaryEvent(
        String id,
        String name,
        List<String> incomingFlows,
        List<String> outgoingFlows,
        String attachedToRef
) implements FlowNode {

    @Override
    public NodeType type() {
        return NodeType.COMPENSATION_BOUNDARY;
    }
}
