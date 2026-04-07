package uz.salvadore.processengine.core.engine.handler;

import uz.salvadore.processengine.core.domain.event.ProcessEvent;
import uz.salvadore.processengine.core.domain.event.TimerScheduledEvent;
import uz.salvadore.processengine.core.domain.model.FlowNode;
import uz.salvadore.processengine.core.domain.model.TimerBoundaryEvent;
import uz.salvadore.processengine.core.domain.model.TimerDefinition;
import uz.salvadore.processengine.core.domain.model.Token;
import uz.salvadore.processengine.core.engine.context.ExecutionContext;
import uz.salvadore.processengine.core.port.outgoing.SequenceGenerator;
import uz.salvadore.processengine.core.port.outgoing.TimerService;
import uz.salvadore.processengine.core.util.UUIDv7;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Handles TimerBoundaryEvent: schedules a timer via TimerService.
 * When the timer fires, the attached task is cancelled (if cancelActivity=true)
 * and the flow is redirected to the timer's outgoing path.
 */
public final class TimerBoundaryEventHandler implements NodeHandler {

    private final TimerService timerService;
    private final SequenceGenerator eventSequencer;

    public TimerBoundaryEventHandler(TimerService timerService, SequenceGenerator eventSequencer) {
        this.timerService = timerService;
        this.eventSequencer = eventSequencer;
    }

    @Override
    public List<ProcessEvent> handle(Token token, FlowNode node, ExecutionContext context) {
        TimerBoundaryEvent timerEvent = (TimerBoundaryEvent) node;
        TimerDefinition timerDef = timerEvent.timerDefinition();

        Duration scheduleDuration = switch (timerDef.type()) {
            case DURATION -> timerDef.asDuration();
            case DATE -> {
                Duration until = Duration.between(Instant.now(), timerDef.asDate());
                yield until.isNegative() ? Duration.ZERO : until;
            }
            case CYCLE -> timerDef.asCycle().interval();
        };

        timerService.schedule(
                context.getProcessInstance().getId(),
                token.getId(),
                node.id(),
                scheduleDuration,
                callback -> {}
        );

        TimerScheduledEvent scheduledEvent = new TimerScheduledEvent(
                UUIDv7.generate(),
                context.getProcessInstance().getId(),
                token.getId(),
                node.id(),
                scheduleDuration,
                Instant.now(),
                eventSequencer.next(context.getProcessInstance().getId())
        );

        return List.of(scheduledEvent);
    }
}
