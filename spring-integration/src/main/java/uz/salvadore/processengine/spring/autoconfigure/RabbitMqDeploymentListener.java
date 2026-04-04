package uz.salvadore.processengine.spring.autoconfigure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uz.salvadore.processengine.core.domain.model.ProcessDefinition;
import uz.salvadore.processengine.core.domain.model.ServiceTask;
import uz.salvadore.processengine.core.port.outgoing.DeploymentListener;
import uz.salvadore.processengine.core.port.outgoing.MessageTransport;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Registers result callbacks for all ServiceTask topics when a process definition
 * is deployed. Topology (shared queues) is created at startup, not here.
 */
public class RabbitMqDeploymentListener implements DeploymentListener {

    private static final Logger log = LoggerFactory.getLogger(RabbitMqDeploymentListener.class);

    private final MessageTransport messageTransport;
    private final Consumer<MessageTransport.MessageResult> resultCallback;
    private final Set<String> subscribedTopics = ConcurrentHashMap.newKeySet();

    public RabbitMqDeploymentListener(MessageTransport messageTransport,
                                      Consumer<MessageTransport.MessageResult> resultCallback) {
        this.messageTransport = messageTransport;
        this.resultCallback = resultCallback;
    }

    @Override
    public void onDeploy(ProcessDefinition definition) {
        definition.getFlowNodes().stream()
                .filter(ServiceTask.class::isInstance)
                .map(ServiceTask.class::cast)
                .map(ServiceTask::topic)
                .distinct()
                .forEach(topic -> {
                    if (subscribedTopics.add(topic)) {
                        messageTransport.subscribe(topic, resultCallback);
                        log.info("Registered result callback for topic '{}' (definition: {})",
                                topic, definition.getKey());
                    }
                });
    }
}
