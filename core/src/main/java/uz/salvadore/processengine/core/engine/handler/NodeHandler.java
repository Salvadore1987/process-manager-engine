package uz.salvadore.processengine.core.engine.handler;

import uz.salvadore.processengine.core.domain.event.ProcessEvent;
import uz.salvadore.processengine.core.domain.model.FlowNode;
import uz.salvadore.processengine.core.domain.model.Token;
import uz.salvadore.processengine.core.engine.context.ExecutionContext;

import java.util.List;

/**
 * Strategy interface for handling token arrival at a specific FlowNode type.
 * Returns a list of events produced by the execution.
 */
public interface NodeHandler {

    List<ProcessEvent> handle(Token token, FlowNode node, ExecutionContext context);
}
