package uz.salvadore.processengine.spring.autoconfigure;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import uz.salvadore.processengine.core.adapter.inmemory.InMemoryActivityLog;
import uz.salvadore.processengine.core.adapter.inmemory.InMemoryInstanceDefinitionMapping;
import uz.salvadore.processengine.core.adapter.inmemory.InMemoryProcessDefinitionStore;
import uz.salvadore.processengine.core.adapter.inmemory.InMemoryProcessInstanceLock;
import uz.salvadore.processengine.core.adapter.inmemory.InMemorySequenceGenerator;
import uz.salvadore.processengine.core.domain.enums.NodeType;
import uz.salvadore.processengine.core.engine.ProcessEngine;
import uz.salvadore.processengine.core.engine.TokenExecutor;
import uz.salvadore.processengine.core.engine.condition.ConditionEvaluator;
import uz.salvadore.processengine.core.engine.condition.SimpleConditionEvaluator;
import uz.salvadore.processengine.core.engine.handler.CallActivityHandler;
import uz.salvadore.processengine.core.engine.handler.CompensationBoundaryEventHandler;
import uz.salvadore.processengine.core.engine.handler.EndEventHandler;
import uz.salvadore.processengine.core.engine.handler.ErrorBoundaryEventHandler;
import uz.salvadore.processengine.core.engine.handler.ExclusiveGatewayHandler;
import uz.salvadore.processengine.core.engine.handler.NodeHandler;
import uz.salvadore.processengine.core.engine.handler.ParallelGatewayHandler;
import uz.salvadore.processengine.core.engine.handler.ServiceTaskHandler;
import uz.salvadore.processengine.core.engine.handler.StartEventHandler;
import uz.salvadore.processengine.core.engine.handler.TimerBoundaryEventHandler;
import uz.salvadore.processengine.core.engine.handler.TimerIntermediateCatchEventHandler;
import uz.salvadore.processengine.core.port.outgoing.ActivityLog;
import uz.salvadore.processengine.core.port.outgoing.DeploymentListener;
import uz.salvadore.processengine.core.port.outgoing.InstanceDefinitionMapping;
import uz.salvadore.processengine.core.port.outgoing.MessageTransport;
import uz.salvadore.processengine.core.port.outgoing.ProcessDefinitionStore;
import uz.salvadore.processengine.core.port.outgoing.ProcessEventStore;
import uz.salvadore.processengine.core.port.outgoing.ProcessInstanceLock;
import uz.salvadore.processengine.core.port.outgoing.SequenceGenerator;
import uz.salvadore.processengine.core.port.outgoing.TimerService;
import java.util.List;
import java.util.Map;

@AutoConfiguration
@EnableConfigurationProperties(ProcessEngineProperties.class)
public class ProcessEngineAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public SequenceGenerator sequenceGenerator() {
        return new InMemorySequenceGenerator();
    }

    @Bean
    @ConditionalOnMissingBean
    public ConditionEvaluator conditionEvaluator() {
        return new SimpleConditionEvaluator();
    }

    @Bean
    @ConditionalOnMissingBean
    public ProcessDefinitionStore processDefinitionStore() {
        return new InMemoryProcessDefinitionStore();
    }

    @Bean
    @ConditionalOnMissingBean
    public InstanceDefinitionMapping instanceDefinitionMapping() {
        return new InMemoryInstanceDefinitionMapping();
    }

    @Bean
    @ConditionalOnMissingBean
    public ProcessInstanceLock processInstanceLock() {
        return new InMemoryProcessInstanceLock();
    }

    @Bean
    @ConditionalOnMissingBean
    public ActivityLog activityLog() {
        return new InMemoryActivityLog();
    }

    @Bean
    @ConditionalOnMissingBean
    public TokenExecutor tokenExecutor(SequenceGenerator sequenceGenerator,
                                       ConditionEvaluator conditionEvaluator,
                                       MessageTransport messageTransport,
                                       TimerService timerService) {
        Map<NodeType, NodeHandler> handlers = Map.ofEntries(
                Map.entry(NodeType.START_EVENT, new StartEventHandler(sequenceGenerator)),
                Map.entry(NodeType.END_EVENT, new EndEventHandler(sequenceGenerator)),
                Map.entry(NodeType.SERVICE_TASK, new ServiceTaskHandler(messageTransport)),
                Map.entry(NodeType.EXCLUSIVE_GATEWAY, new ExclusiveGatewayHandler(conditionEvaluator, sequenceGenerator)),
                Map.entry(NodeType.PARALLEL_GATEWAY, new ParallelGatewayHandler(sequenceGenerator)),
                Map.entry(NodeType.CALL_ACTIVITY, new CallActivityHandler(sequenceGenerator)),
                Map.entry(NodeType.TIMER_BOUNDARY, new TimerBoundaryEventHandler(timerService, sequenceGenerator)),
                Map.entry(NodeType.TIMER_INTERMEDIATE_CATCH, new TimerIntermediateCatchEventHandler(timerService, sequenceGenerator)),
                Map.entry(NodeType.ERROR_BOUNDARY, new ErrorBoundaryEventHandler(sequenceGenerator)),
                Map.entry(NodeType.COMPENSATION_BOUNDARY, new CompensationBoundaryEventHandler(sequenceGenerator))
        );
        return new TokenExecutor(handlers);
    }

    @Bean
    @ConditionalOnMissingBean
    public ProcessEngine processEngine(ProcessEventStore eventStore,
                                       ProcessDefinitionStore definitionStore,
                                       TokenExecutor tokenExecutor,
                                       SequenceGenerator sequenceGenerator,
                                       InstanceDefinitionMapping instanceDefinitionMapping,
                                       ProcessInstanceLock processInstanceLock,
                                       ActivityLog activityLog,
                                       List<DeploymentListener> deploymentListeners) {
        return new ProcessEngine(eventStore, definitionStore, tokenExecutor, sequenceGenerator,
                instanceDefinitionMapping, processInstanceLock, activityLog, deploymentListeners);
    }

    @Bean
    @ConditionalOnMissingBean(name = "rabbitMqDeploymentListener")
    @ConditionalOnBean(MessageTransport.class)
    public DeploymentListener rabbitMqDeploymentListener(
            MessageTransport messageTransport,
            ObjectProvider<ProcessEngine> processEngineProvider) {
        return new RabbitMqDeploymentListener(
                messageTransport,
                taskResultCallback(processEngineProvider)
        );
    }

    @Bean
    @ConditionalOnMissingBean(name = "rabbitMqSubscriptionRecovery")
    @ConditionalOnBean(MessageTransport.class)
    public RabbitMqSubscriptionRecovery rabbitMqSubscriptionRecovery(
            ProcessDefinitionStore definitionStore,
            MessageTransport messageTransport,
            ObjectProvider<ProcessEngine> processEngineProvider) {
        return new RabbitMqSubscriptionRecovery(
                definitionStore,
                messageTransport,
                taskResultCallback(processEngineProvider)
        );
    }

    private java.util.function.Consumer<MessageTransport.MessageResult> taskResultCallback(
            ObjectProvider<ProcessEngine> processEngineProvider) {
        return result -> {
            if (result.success()) {
                processEngineProvider.getObject().completeTask(result.correlationId(), result.payload());
            } else {
                processEngineProvider.getObject().failTask(result.correlationId(), result.errorCode(),
                        String.valueOf(result.payload().get("message")));
            }
        };
    }
}
