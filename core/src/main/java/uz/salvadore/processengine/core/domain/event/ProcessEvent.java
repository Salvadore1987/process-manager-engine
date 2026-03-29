package uz.salvadore.processengine.core.domain.event;

import java.time.Instant;
import java.util.UUID;

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
        CallActivityCompletedEvent {

    UUID id();

    UUID processInstanceId();

    Instant occurredAt();

    long sequenceNumber();
}
