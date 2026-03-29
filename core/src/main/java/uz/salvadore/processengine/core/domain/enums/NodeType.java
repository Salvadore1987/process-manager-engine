package uz.salvadore.processengine.core.domain.enums;

public enum NodeType {
    START_EVENT,
    END_EVENT,
    SERVICE_TASK,
    EXCLUSIVE_GATEWAY,
    PARALLEL_GATEWAY,
    CALL_ACTIVITY,
    COMPENSATION_BOUNDARY,
    TIMER_BOUNDARY,
    ERROR_BOUNDARY
}
