package uz.salvadore.processengine.core.port.outgoing;

import uz.salvadore.processengine.core.domain.model.ProcessDefinition;

/**
 * Callback invoked when a process definition is deployed.
 * Implementations can react to deployments, e.g. by initializing messaging infrastructure.
 */
public interface DeploymentListener {

    void onDeploy(ProcessDefinition definition);
}
