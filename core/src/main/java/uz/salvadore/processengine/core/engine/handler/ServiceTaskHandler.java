package uz.salvadore.processengine.core.engine.handler;

import uz.salvadore.processengine.core.domain.event.ProcessEvent;
import uz.salvadore.processengine.core.domain.model.FlowNode;
import uz.salvadore.processengine.core.domain.model.ServiceTask;
import uz.salvadore.processengine.core.domain.model.Token;
import uz.salvadore.processengine.core.engine.context.ExecutionContext;
import uz.salvadore.processengine.core.port.outgoing.MessageTransport;

import java.util.List;

/**
 * Handles ServiceTask: sets the token to WAITING state and sends a message
 * via MessageTransport to the external service.
 * The token will be advanced when a task completion message is received.
 */
public final class ServiceTaskHandler implements NodeHandler {

    private final MessageTransport messageTransport;

    public ServiceTaskHandler(MessageTransport messageTransport) {
        this.messageTransport = messageTransport;
    }

    @Override
    public List<ProcessEvent> handle(Token token, FlowNode node, ExecutionContext context) {
        ServiceTask serviceTask = (ServiceTask) node;

        messageTransport.send(
                serviceTask.topic(),
                token.getId(),
                context.getVariables()
        );

        return List.of();
    }
}
