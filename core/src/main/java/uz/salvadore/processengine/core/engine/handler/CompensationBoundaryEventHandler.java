package uz.salvadore.processengine.core.engine.handler;

import uz.salvadore.processengine.core.domain.event.CompensationTriggeredEvent;
import uz.salvadore.processengine.core.domain.event.ProcessEvent;
import uz.salvadore.processengine.core.domain.model.CompensationBoundaryEvent;
import uz.salvadore.processengine.core.domain.model.FlowNode;
import uz.salvadore.processengine.core.domain.model.SequenceFlow;
import uz.salvadore.processengine.core.domain.model.Token;
import uz.salvadore.processengine.core.engine.context.ExecutionContext;
import uz.salvadore.processengine.core.port.outgoing.SequenceGenerator;
import uz.salvadore.processengine.core.util.UUIDv7;

import java.time.Instant;
import java.util.List;

/**
 * Handles CompensationBoundaryEvent: triggers the compensation task
 * associated with the boundary event.
 */
public final class CompensationBoundaryEventHandler implements NodeHandler {

    private final SequenceGenerator eventSequencer;

    public CompensationBoundaryEventHandler(SequenceGenerator eventSequencer) {
        this.eventSequencer = eventSequencer;
    }

    @Override
    public List<ProcessEvent> handle(Token token, FlowNode node, ExecutionContext context) {
        CompensationBoundaryEvent compensationEvent = (CompensationBoundaryEvent) node;

        List<SequenceFlow> outgoingFlows = context.findOutgoingFlows(node.id());
        String compensationTaskId = outgoingFlows.isEmpty()
                ? null
                : outgoingFlows.getFirst().targetRef();

        CompensationTriggeredEvent triggeredEvent = new CompensationTriggeredEvent(
                UUIDv7.generate(),
                context.getProcessInstance().getId(),
                compensationEvent.attachedToRef(),
                compensationTaskId,
                Instant.now(),
                eventSequencer.next(context.getProcessInstance().getId())
        );

        return List.of(triggeredEvent);
    }
}
