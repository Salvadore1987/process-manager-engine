package uz.salvadore.processengine.core.engine.handler;

import uz.salvadore.processengine.core.domain.enums.TokenState;
import uz.salvadore.processengine.core.domain.event.ProcessCompletedEvent;
import uz.salvadore.processengine.core.domain.event.ProcessErrorEvent;
import uz.salvadore.processengine.core.domain.event.ProcessEvent;
import uz.salvadore.processengine.core.domain.event.TaskCompletedEvent;
import uz.salvadore.processengine.core.domain.model.EndEvent;
import uz.salvadore.processengine.core.domain.model.FlowNode;
import uz.salvadore.processengine.core.domain.model.Token;
import uz.salvadore.processengine.core.engine.context.ExecutionContext;
import uz.salvadore.processengine.core.port.outgoing.SequenceGenerator;
import uz.salvadore.processengine.core.util.UUIDv7;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Handles EndEvent: completes the current token.
 * If the EndEvent has an errorCode, emits a ProcessErrorEvent.
 * If all tokens are completed after this one, emits a ProcessCompletedEvent.
 */
public final class EndEventHandler implements NodeHandler {

    private final SequenceGenerator eventSequencer;

    public EndEventHandler(SequenceGenerator eventSequencer) {
        this.eventSequencer = eventSequencer;
    }

    @Override
    public List<ProcessEvent> handle(Token token, FlowNode node, ExecutionContext context) {
        EndEvent endEvent = (EndEvent) node;
        UUID processInstanceId = context.getProcessInstance().getId();
        List<ProcessEvent> events = new ArrayList<>();

        TaskCompletedEvent completedEvent = new TaskCompletedEvent(
                UUIDv7.generate(),
                processInstanceId,
                token.getId(),
                node.id(),
                null,
                Instant.now(),
                eventSequencer.next(processInstanceId)
        );
        events.add(completedEvent);

        if (endEvent.errorCode() != null) {
            ProcessErrorEvent errorEvent = new ProcessErrorEvent(
                    UUIDv7.generate(),
                    processInstanceId,
                    endEvent.errorCode(),
                    "Process ended with error: " + endEvent.errorCode(),
                    Instant.now(),
                    eventSequencer.next(processInstanceId)
            );
            events.add(errorEvent);
        } else {
            boolean allTokensCompleted = context.getProcessInstance().getTokens().stream()
                    .filter(t -> !t.getId().equals(token.getId()))
                    .allMatch(t -> t.getState() == TokenState.COMPLETED);

            if (allTokensCompleted) {
                ProcessCompletedEvent processCompleted = new ProcessCompletedEvent(
                        UUIDv7.generate(),
                        processInstanceId,
                        Instant.now(),
                        eventSequencer.next(processInstanceId)
                );
                events.add(processCompleted);
            }
        }

        return events;
    }
}
