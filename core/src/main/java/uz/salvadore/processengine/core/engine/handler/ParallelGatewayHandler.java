package uz.salvadore.processengine.core.engine.handler;

import uz.salvadore.processengine.core.domain.enums.TokenState;
import uz.salvadore.processengine.core.domain.event.ProcessEvent;
import uz.salvadore.processengine.core.domain.event.TaskCompletedEvent;
import uz.salvadore.processengine.core.domain.event.TokenMovedEvent;
import uz.salvadore.processengine.core.domain.model.FlowNode;
import uz.salvadore.processengine.core.domain.model.SequenceFlow;
import uz.salvadore.processengine.core.domain.model.Token;
import uz.salvadore.processengine.core.engine.context.ExecutionContext;
import uz.salvadore.processengine.core.port.outgoing.SequenceGenerator;
import uz.salvadore.processengine.core.util.UUIDv7;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Handles ParallelGateway for both fork and join semantics.
 * Fork (1 incoming, N outgoing): creates N tokens, one per outgoing flow.
 * Join (N incoming, 1 outgoing): waits until all incoming flows have tokens,
 * then completes waiting tokens and creates one outgoing token.
 */
public final class ParallelGatewayHandler implements NodeHandler {

    private final SequenceGenerator eventSequencer;

    public ParallelGatewayHandler(SequenceGenerator eventSequencer) {
        this.eventSequencer = eventSequencer;
    }

    @Override
    public List<ProcessEvent> handle(Token token, FlowNode node, ExecutionContext context) {
        List<SequenceFlow> outgoingFlows = context.findOutgoingFlows(node.id());
        List<SequenceFlow> incomingFlows = context.findIncomingFlows(node.id());

        if (incomingFlows.size() <= 1) {
            return handleFork(token, node, outgoingFlows, context);
        } else {
            return handleJoin(token, node, incomingFlows, outgoingFlows, context);
        }
    }

    private List<ProcessEvent> handleFork(Token token, FlowNode node,
                                           List<SequenceFlow> outgoingFlows,
                                           ExecutionContext context) {
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

        for (SequenceFlow flow : outgoingFlows) {
            Token newToken = Token.create(node.id());
            TokenMovedEvent movedEvent = new TokenMovedEvent(
                    UUIDv7.generate(),
                    processInstanceId,
                    newToken.getId(),
                    node.id(),
                    flow.targetRef(),
                    Instant.now(),
                    eventSequencer.next(processInstanceId)
            );
            events.add(movedEvent);
        }

        return events;
    }

    private List<ProcessEvent> handleJoin(Token token, FlowNode node,
                                           List<SequenceFlow> incomingFlows,
                                           List<SequenceFlow> outgoingFlows,
                                           ExecutionContext context) {
        UUID processInstanceId = context.getProcessInstance().getId();
        List<Token> tokensAtGateway = context.getProcessInstance().getTokens().stream()
                .filter(t -> t.getCurrentNodeId().equals(node.id()))
                .filter(t -> t.getState() == TokenState.ACTIVE)
                .toList();

        int tokensNeeded = incomingFlows.size();
        int tokensPresent = tokensAtGateway.size();

        if (tokensPresent < tokensNeeded) {
            return List.of();
        }

        List<ProcessEvent> events = new ArrayList<>();

        for (Token waitingToken : tokensAtGateway) {
            TaskCompletedEvent completedEvent = new TaskCompletedEvent(
                    UUIDv7.generate(),
                    processInstanceId,
                    waitingToken.getId(),
                    node.id(),
                    null,
                    Instant.now(),
                    eventSequencer.next(processInstanceId)
            );
            events.add(completedEvent);
        }

        if (!outgoingFlows.isEmpty()) {
            Token newToken = Token.create(node.id());
            TokenMovedEvent movedEvent = new TokenMovedEvent(
                    UUIDv7.generate(),
                    processInstanceId,
                    newToken.getId(),
                    node.id(),
                    outgoingFlows.getFirst().targetRef(),
                    Instant.now(),
                    eventSequencer.next(processInstanceId)
            );
            events.add(movedEvent);
        }

        return events;
    }
}
