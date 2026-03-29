package uz.salvadore.processengine.core.domain.model;

import uz.salvadore.processengine.core.domain.enums.NodeType;

import java.util.List;

public sealed interface FlowNode permits
        StartEvent,
        EndEvent,
        ServiceTask,
        ExclusiveGateway,
        ParallelGateway,
        CallActivity,
        CompensationBoundaryEvent,
        TimerBoundaryEvent,
        ErrorBoundaryEvent {

    String id();

    String name();

    NodeType type();

    List<String> incomingFlows();

    List<String> outgoingFlows();
}
