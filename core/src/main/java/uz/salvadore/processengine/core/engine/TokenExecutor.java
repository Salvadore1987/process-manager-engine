package uz.salvadore.processengine.core.engine;

import uz.salvadore.processengine.core.domain.enums.NodeType;
import uz.salvadore.processengine.core.domain.event.ProcessEvent;
import uz.salvadore.processengine.core.domain.model.FlowNode;
import uz.salvadore.processengine.core.domain.model.Token;
import uz.salvadore.processengine.core.engine.context.ExecutionContext;
import uz.salvadore.processengine.core.engine.handler.NodeHandler;

import java.util.List;
import java.util.Map;

/**
 * Dispatches token execution to the correct NodeHandler based on the FlowNode's type.
 */
public final class TokenExecutor {

    private final Map<NodeType, NodeHandler> handlers;

    public TokenExecutor(Map<NodeType, NodeHandler> handlers) {
        this.handlers = handlers;
    }

    public List<ProcessEvent> execute(Token token, FlowNode node, ExecutionContext context) {
        NodeHandler handler = handlers.get(node.type());
        if (handler == null) {
            throw new IllegalStateException("No handler registered for node type: " + node.type());
        }
        return handler.handle(token, node, context);
    }
}
