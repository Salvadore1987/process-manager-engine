package uz.salvadore.processengine.core.domain.model;

import uz.salvadore.processengine.core.domain.enums.NodeType;

import java.time.Duration;
import java.util.List;

public record TimerBoundaryEvent(
        String id,
        String name,
        List<String> incomingFlows,
        List<String> outgoingFlows,
        String attachedToRef,
        Duration duration,
        boolean cancelActivity
) implements FlowNode {

    @Override
    public NodeType type() {
        return NodeType.TIMER_BOUNDARY;
    }
}
