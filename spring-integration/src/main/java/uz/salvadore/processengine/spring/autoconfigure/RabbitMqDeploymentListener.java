package uz.salvadore.processengine.spring.autoconfigure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uz.salvadore.processengine.core.domain.model.ProcessDefinition;
import uz.salvadore.processengine.core.domain.model.ServiceTask;
import uz.salvadore.processengine.core.port.outgoing.DeploymentListener;
import uz.salvadore.processengine.rabbitmq.RabbitMqTopologyInitializer;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

/**
 * Creates RabbitMQ queues and bindings for all ServiceTask topics
 * when a process definition is deployed.
 */
public class RabbitMqDeploymentListener implements DeploymentListener {

    private static final Logger log = LoggerFactory.getLogger(RabbitMqDeploymentListener.class);

    private final RabbitMqTopologyInitializer topologyInitializer;

    public RabbitMqDeploymentListener(RabbitMqTopologyInitializer topologyInitializer) {
        this.topologyInitializer = topologyInitializer;
    }

    @Override
    public void onDeploy(ProcessDefinition definition) {
        definition.getFlowNodes().stream()
                .filter(ServiceTask.class::isInstance)
                .map(ServiceTask.class::cast)
                .map(ServiceTask::topic)
                .distinct()
                .forEach(topic -> {
                    try {
                        topologyInitializer.ensureTopicQueues(topic);
                        log.info("Initialized RabbitMQ queues for topic '{}' (definition: {})",
                                topic, definition.getKey());
                    } catch (IOException | TimeoutException e) {
                        throw new RuntimeException(
                                "Failed to initialize RabbitMQ queues for topic: " + topic, e);
                    }
                });
    }
}
