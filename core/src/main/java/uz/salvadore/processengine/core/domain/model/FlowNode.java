package uz.salvadore.processengine.core.domain.model;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import uz.salvadore.processengine.core.domain.enums.NodeType;

import java.util.List;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "@type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = StartEvent.class, name = "START_EVENT"),
        @JsonSubTypes.Type(value = EndEvent.class, name = "END_EVENT"),
        @JsonSubTypes.Type(value = ServiceTask.class, name = "SERVICE_TASK"),
        @JsonSubTypes.Type(value = ExclusiveGateway.class, name = "EXCLUSIVE_GATEWAY"),
        @JsonSubTypes.Type(value = ParallelGateway.class, name = "PARALLEL_GATEWAY"),
        @JsonSubTypes.Type(value = CallActivity.class, name = "CALL_ACTIVITY"),
        @JsonSubTypes.Type(value = CompensationBoundaryEvent.class, name = "COMPENSATION_BOUNDARY"),
        @JsonSubTypes.Type(value = TimerBoundaryEvent.class, name = "TIMER_BOUNDARY"),
        @JsonSubTypes.Type(value = ErrorBoundaryEvent.class, name = "ERROR_BOUNDARY"),
        @JsonSubTypes.Type(value = TimerIntermediateCatchEvent.class, name = "TIMER_INTERMEDIATE_CATCH")
})
public sealed interface FlowNode permits
        StartEvent,
        EndEvent,
        ServiceTask,
        ExclusiveGateway,
        ParallelGateway,
        CallActivity,
        CompensationBoundaryEvent,
        TimerBoundaryEvent,
        ErrorBoundaryEvent,
        TimerIntermediateCatchEvent {

    String id();

    String name();

    NodeType type();

    List<String> incomingFlows();

    List<String> outgoingFlows();
}
