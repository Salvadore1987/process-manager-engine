package uz.salvadore.processengine.core.engine.handler;

import uz.salvadore.processengine.core.domain.event.ProcessEvent;
import uz.salvadore.processengine.core.domain.event.TokenMovedEvent;
import uz.salvadore.processengine.core.domain.model.ExclusiveGateway;
import uz.salvadore.processengine.core.domain.model.FlowNode;
import uz.salvadore.processengine.core.domain.model.SequenceFlow;
import uz.salvadore.processengine.core.domain.model.Token;
import uz.salvadore.processengine.core.engine.condition.ConditionEvaluator;
import uz.salvadore.processengine.core.engine.context.ExecutionContext;
import uz.salvadore.processengine.core.port.outgoing.SequenceGenerator;
import uz.salvadore.processengine.core.util.UUIDv7;

import java.time.Instant;
import java.util.List;

/**
 * Handles ExclusiveGateway: evaluates conditions on outgoing flows
 * and routes the token to the first matching flow.
 * Supports explicit default flow via defaultFlowId attribute,
 * with fallback to implicit default (flow without condition).
 */
public final class ExclusiveGatewayHandler implements NodeHandler {

    private final ConditionEvaluator conditionEvaluator;
    private final SequenceGenerator eventSequencer;

    public ExclusiveGatewayHandler(ConditionEvaluator conditionEvaluator, SequenceGenerator eventSequencer) {
        this.conditionEvaluator = conditionEvaluator;
        this.eventSequencer = eventSequencer;
    }

    @Override
    public List<ProcessEvent> handle(Token token, FlowNode node, ExecutionContext context) {
        ExclusiveGateway gateway = (ExclusiveGateway) node;
        List<SequenceFlow> outgoingFlows = context.findOutgoingFlows(node.id());
        if (outgoingFlows.isEmpty()) {
            throw new IllegalStateException("ExclusiveGateway '" + node.id() + "' has no outgoing flows");
        }

        String defaultFlowId = gateway.defaultFlowId();
        SequenceFlow defaultFlow = null;

        for (SequenceFlow flow : outgoingFlows) {
            if (flow.id().equals(defaultFlowId)) {
                defaultFlow = flow;
                continue;
            }
            if (flow.conditionExpression() == null) {
                if (defaultFlow == null) {
                    defaultFlow = flow;
                }
                continue;
            }
            if (conditionEvaluator.evaluate(flow.conditionExpression().expression(), context.getVariables())) {
                return List.of(createTokenMovedEvent(token, node, flow, context));
            }
        }

        if (defaultFlow != null) {
            return List.of(createTokenMovedEvent(token, node, defaultFlow, context));
        }

        throw new IllegalStateException(
                "No matching condition found for ExclusiveGateway '" + node.id() + "'");
    }

    private TokenMovedEvent createTokenMovedEvent(Token token, FlowNode node, SequenceFlow flow,
                                                   ExecutionContext context) {
        return new TokenMovedEvent(
                UUIDv7.generate(),
                context.getProcessInstance().getId(),
                token.getId(),
                node.id(),
                flow.targetRef(),
                Instant.now(),
                eventSequencer.next(context.getProcessInstance().getId())
        );
    }
}
