package uz.salvadore.processengine.core.domain.model;

import uz.salvadore.processengine.core.domain.enums.NodeType;

import java.util.List;

public record TimerIntermediateCatchEvent(
        String id,
        String name,
        List<String> incomingFlows,
        List<String> outgoingFlows,
        TimerDefinition timerDefinition
) implements FlowNode {

    @Override
    public NodeType type() {
        return NodeType.TIMER_INTERMEDIATE_CATCH;
    }
}
