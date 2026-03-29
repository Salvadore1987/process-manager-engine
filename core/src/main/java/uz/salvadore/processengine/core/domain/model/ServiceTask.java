package uz.salvadore.processengine.core.domain.model;

import uz.salvadore.processengine.core.domain.enums.NodeType;

import java.time.Duration;
import java.util.List;

public record ServiceTask(
        String id,
        String name,
        List<String> incomingFlows,
        List<String> outgoingFlows,
        String topic,
        int retryCount,
        Duration retryInterval
) implements FlowNode {

    @Override
    public NodeType type() {
        return NodeType.SERVICE_TASK;
    }
}
