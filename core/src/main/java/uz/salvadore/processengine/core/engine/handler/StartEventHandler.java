package uz.salvadore.processengine.core.engine.handler;

import uz.salvadore.processengine.core.domain.event.ProcessEvent;
import uz.salvadore.processengine.core.domain.event.TokenMovedEvent;
import uz.salvadore.processengine.core.domain.model.FlowNode;
import uz.salvadore.processengine.core.domain.model.SequenceFlow;
import uz.salvadore.processengine.core.domain.model.Token;
import uz.salvadore.processengine.core.engine.context.ExecutionContext;
import uz.salvadore.processengine.core.port.outgoing.SequenceGenerator;
import uz.salvadore.processengine.core.util.UUIDv7;

import java.time.Instant;
import java.util.List;

/**
 * Handles StartEvent: moves the token to the first outgoing flow's target node.
 */
public final class StartEventHandler implements NodeHandler {

    private final SequenceGenerator eventSequencer;

    public StartEventHandler(SequenceGenerator eventSequencer) {
        this.eventSequencer = eventSequencer;
    }

    @Override
    public List<ProcessEvent> handle(Token token, FlowNode node, ExecutionContext context) {
        List<SequenceFlow> outgoingFlows = context.findOutgoingFlows(node.id());
        if (outgoingFlows.isEmpty()) {
            throw new IllegalStateException("StartEvent '" + node.id() + "' has no outgoing flows");
        }

        SequenceFlow firstFlow = outgoingFlows.getFirst();
        TokenMovedEvent movedEvent = new TokenMovedEvent(
                UUIDv7.generate(),
                context.getProcessInstance().getId(),
                token.getId(),
                node.id(),
                firstFlow.targetRef(),
                Instant.now(),
                eventSequencer.next(context.getProcessInstance().getId())
        );

        return List.of(movedEvent);
    }
}
