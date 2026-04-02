package uz.salvadore.processengine.core.engine.handler;

import uz.salvadore.processengine.core.domain.event.ProcessEvent;
import uz.salvadore.processengine.core.domain.event.TokenMovedEvent;
import uz.salvadore.processengine.core.domain.model.ErrorBoundaryEvent;
import uz.salvadore.processengine.core.domain.model.FlowNode;
import uz.salvadore.processengine.core.domain.model.SequenceFlow;
import uz.salvadore.processengine.core.domain.model.Token;
import uz.salvadore.processengine.core.engine.context.ExecutionContext;
import uz.salvadore.processengine.core.port.outgoing.SequenceGenerator;
import uz.salvadore.processengine.core.util.UUIDv7;

import java.time.Instant;
import java.util.List;

/**
 * Handles ErrorBoundaryEvent: catches errors matching the errorCode
 * and redirects the flow to the boundary event's outgoing path.
 */
public final class ErrorBoundaryEventHandler implements NodeHandler {

    private final SequenceGenerator eventSequencer;

    public ErrorBoundaryEventHandler(SequenceGenerator eventSequencer) {
        this.eventSequencer = eventSequencer;
    }

    @Override
    public List<ProcessEvent> handle(Token token, FlowNode node, ExecutionContext context) {
        ErrorBoundaryEvent errorBoundary = (ErrorBoundaryEvent) node;
        List<SequenceFlow> outgoingFlows = context.findOutgoingFlows(node.id());

        if (outgoingFlows.isEmpty()) {
            throw new IllegalStateException(
                    "ErrorBoundaryEvent '" + node.id() + "' has no outgoing flows");
        }

        SequenceFlow outgoingFlow = outgoingFlows.getFirst();
        TokenMovedEvent movedEvent = new TokenMovedEvent(
                UUIDv7.generate(),
                context.getProcessInstance().getId(),
                token.getId(),
                node.id(),
                outgoingFlow.targetRef(),
                Instant.now(),
                eventSequencer.next(context.getProcessInstance().getId())
        );

        return List.of(movedEvent);
    }
}
