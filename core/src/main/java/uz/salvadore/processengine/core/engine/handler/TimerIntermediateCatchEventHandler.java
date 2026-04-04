package uz.salvadore.processengine.core.engine.handler;

import uz.salvadore.processengine.core.domain.event.ProcessEvent;
import uz.salvadore.processengine.core.domain.event.TimerScheduledEvent;
import uz.salvadore.processengine.core.domain.model.FlowNode;
import uz.salvadore.processengine.core.domain.model.TimerDefinition;
import uz.salvadore.processengine.core.domain.model.TimerIntermediateCatchEvent;
import uz.salvadore.processengine.core.domain.model.Token;
import uz.salvadore.processengine.core.engine.context.ExecutionContext;
import uz.salvadore.processengine.core.port.outgoing.SequenceGenerator;
import uz.salvadore.processengine.core.port.outgoing.TimerService;
import uz.salvadore.processengine.core.util.UUIDv7;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Handles Timer Intermediate Catch Event: schedules a timer via TimerService.
 * The token remains in WAITING state until the timer fires, then advances
 * to the next node along the outgoing sequence flow.
 */
public final class TimerIntermediateCatchEventHandler implements NodeHandler {

    private final TimerService timerService;
    private final SequenceGenerator eventSequencer;

    public TimerIntermediateCatchEventHandler(TimerService timerService, SequenceGenerator eventSequencer) {
        this.timerService = timerService;
        this.eventSequencer = eventSequencer;
    }

    @Override
    public List<ProcessEvent> handle(Token token, FlowNode node, ExecutionContext context) {
        TimerIntermediateCatchEvent timerEvent = (TimerIntermediateCatchEvent) node;
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
