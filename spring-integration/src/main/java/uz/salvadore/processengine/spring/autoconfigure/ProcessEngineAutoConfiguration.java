package uz.salvadore.processengine.spring.autoconfigure;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import uz.salvadore.processengine.core.domain.enums.NodeType;
import uz.salvadore.processengine.core.engine.ProcessDefinitionRepository;
import uz.salvadore.processengine.core.engine.ProcessEngine;
import uz.salvadore.processengine.core.engine.TokenExecutor;
import uz.salvadore.processengine.core.engine.condition.ConditionEvaluator;
import uz.salvadore.processengine.core.engine.condition.SimpleConditionEvaluator;
import uz.salvadore.processengine.core.engine.eventsourcing.EventSequencer;
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
import uz.salvadore.processengine.core.port.outgoing.DeploymentListener;
import uz.salvadore.processengine.core.port.outgoing.MessageTransport;
import uz.salvadore.processengine.core.port.outgoing.ProcessEventStore;
import uz.salvadore.processengine.core.port.outgoing.TimerService;
import uz.salvadore.processengine.rabbitmq.RabbitMqTopologyInitializer;

import java.util.List;
import java.util.Map;

@AutoConfiguration
@EnableConfigurationProperties(ProcessEngineProperties.class)
public class ProcessEngineAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public EventSequencer eventSequencer() {
        return new EventSequencer();
    }

    @Bean
    @ConditionalOnMissingBean
    public ConditionEvaluator conditionEvaluator() {
        return new SimpleConditionEvaluator();
    }

    @Bean
    @ConditionalOnMissingBean
    public ProcessDefinitionRepository processDefinitionRepository() {
        return new ProcessDefinitionRepository();
    }

    @Bean
    @ConditionalOnMissingBean
    public TokenExecutor tokenExecutor(EventSequencer eventSequencer,
                                       ConditionEvaluator conditionEvaluator,
                                       MessageTransport messageTransport,
                                       TimerService timerService) {
        Map<NodeType, NodeHandler> handlers = Map.of(
                NodeType.START_EVENT, new StartEventHandler(eventSequencer),
                NodeType.END_EVENT, new EndEventHandler(eventSequencer),
                NodeType.SERVICE_TASK, new ServiceTaskHandler(messageTransport),
                NodeType.EXCLUSIVE_GATEWAY, new ExclusiveGatewayHandler(conditionEvaluator, eventSequencer),
                NodeType.PARALLEL_GATEWAY, new ParallelGatewayHandler(eventSequencer),
                NodeType.CALL_ACTIVITY, new CallActivityHandler(eventSequencer),
                NodeType.TIMER_BOUNDARY, new TimerBoundaryEventHandler(timerService, eventSequencer),
                NodeType.ERROR_BOUNDARY, new ErrorBoundaryEventHandler(eventSequencer),
                NodeType.COMPENSATION_BOUNDARY, new CompensationBoundaryEventHandler(eventSequencer)
        );
        return new TokenExecutor(handlers);
    }

    @Bean
    @ConditionalOnMissingBean
    public ProcessEngine processEngine(ProcessEventStore eventStore,
                                       ProcessDefinitionRepository definitionRepository,
                                       TokenExecutor tokenExecutor,
                                       EventSequencer eventSequencer,
                                       List<DeploymentListener> deploymentListeners) {
        return new ProcessEngine(eventStore, definitionRepository, tokenExecutor, eventSequencer, deploymentListeners);
    }

    @Bean
    @ConditionalOnMissingBean(name = "rabbitMqDeploymentListener")
    @ConditionalOnBean(RabbitMqTopologyInitializer.class)
    public DeploymentListener rabbitMqDeploymentListener(
            RabbitMqTopologyInitializer topologyInitializer,
            MessageTransport messageTransport,
            ObjectProvider<ProcessEngine> processEngineProvider) {
        return new RabbitMqDeploymentListener(
                topologyInitializer,
                messageTransport,
                result -> {
                    if (result.success()) {
                        processEngineProvider.getObject().completeTask(result.correlationId(), result.payload());
                    } else {
                        processEngineProvider.getObject().failTask(result.correlationId(), result.errorCode(), String.valueOf(result.payload().get("message")));
                    }
                }
        );
    }
}
