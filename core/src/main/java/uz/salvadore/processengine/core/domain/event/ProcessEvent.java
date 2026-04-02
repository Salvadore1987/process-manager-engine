package uz.salvadore.processengine.core.domain.event;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.time.Instant;
import java.util.UUID;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "@type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = ProcessStartedEvent.class, name = "PROCESS_STARTED"),
        @JsonSubTypes.Type(value = TokenMovedEvent.class, name = "TOKEN_MOVED"),
        @JsonSubTypes.Type(value = TaskCompletedEvent.class, name = "TASK_COMPLETED"),
        @JsonSubTypes.Type(value = ProcessSuspendedEvent.class, name = "PROCESS_SUSPENDED"),
        @JsonSubTypes.Type(value = ProcessResumedEvent.class, name = "PROCESS_RESUMED"),
        @JsonSubTypes.Type(value = ProcessCompletedEvent.class, name = "PROCESS_COMPLETED"),
        @JsonSubTypes.Type(value = ProcessErrorEvent.class, name = "PROCESS_ERROR"),
        @JsonSubTypes.Type(value = TimerScheduledEvent.class, name = "TIMER_SCHEDULED"),
        @JsonSubTypes.Type(value = TimerFiredEvent.class, name = "TIMER_FIRED"),
        @JsonSubTypes.Type(value = CompensationTriggeredEvent.class, name = "COMPENSATION_TRIGGERED"),
        @JsonSubTypes.Type(value = CallActivityStartedEvent.class, name = "CALL_ACTIVITY_STARTED"),
        @JsonSubTypes.Type(value = CallActivityCompletedEvent.class, name = "CALL_ACTIVITY_COMPLETED"),
        @JsonSubTypes.Type(value = TokenWaitingEvent.class, name = "TOKEN_WAITING")
})
public sealed interface ProcessEvent permits
        ProcessStartedEvent,
        TokenMovedEvent,
        TaskCompletedEvent,
        ProcessSuspendedEvent,
        ProcessResumedEvent,
        ProcessCompletedEvent,
        ProcessErrorEvent,
        TimerScheduledEvent,
        TimerFiredEvent,
        CompensationTriggeredEvent,
        CallActivityStartedEvent,
        CallActivityCompletedEvent,
        TokenWaitingEvent {

    UUID id();

    UUID processInstanceId();

    Instant occurredAt();

    long sequenceNumber();
}
