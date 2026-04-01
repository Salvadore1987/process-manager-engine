package uz.salvadore.processengine.core.engine.handler;

import uz.salvadore.processengine.core.domain.event.CallActivityStartedEvent;
import uz.salvadore.processengine.core.domain.event.ProcessEvent;
import uz.salvadore.processengine.core.domain.model.CallActivity;
import uz.salvadore.processengine.core.domain.model.FlowNode;
import uz.salvadore.processengine.core.domain.model.Token;
import uz.salvadore.processengine.core.engine.context.ExecutionContext;
import uz.salvadore.processengine.core.port.outgoing.SequenceGenerator;
import uz.salvadore.processengine.core.util.UUIDv7;

import java.time.Instant;
import java.util.List;

/**
 * Handles CallActivity: creates a child process instance and sets the parent token to WAITING.
 * The token will be resumed when the child process completes.
 */
public final class CallActivityHandler implements NodeHandler {

    private final SequenceGenerator eventSequencer;

    public CallActivityHandler(SequenceGenerator eventSequencer) {
        this.eventSequencer = eventSequencer;
    }

    @Override
    public List<ProcessEvent> handle(Token token, FlowNode node, ExecutionContext context) {
        CallActivity callActivity = (CallActivity) node;

        CallActivityStartedEvent startedEvent = new CallActivityStartedEvent(
                UUIDv7.generate(),
                context.getProcessInstance().getId(),
                token.getId(),
                node.id(),
                UUIDv7.generate(),
                callActivity.calledElement(),
                Instant.now(),
                eventSequencer.next(context.getProcessInstance().getId())
        );

        return List.of(startedEvent);
    }
}
