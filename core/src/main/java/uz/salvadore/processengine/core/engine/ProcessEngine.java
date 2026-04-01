package uz.salvadore.processengine.core.engine;

import uz.salvadore.processengine.core.domain.enums.NodeType;
import uz.salvadore.processengine.core.domain.enums.ProcessState;
import uz.salvadore.processengine.core.domain.enums.TokenState;
import uz.salvadore.processengine.core.domain.event.CallActivityCompletedEvent;
import uz.salvadore.processengine.core.domain.event.CompensationTriggeredEvent;
import uz.salvadore.processengine.core.domain.event.ProcessErrorEvent;
import uz.salvadore.processengine.core.domain.event.ProcessEvent;
import uz.salvadore.processengine.core.domain.event.ProcessResumedEvent;
import uz.salvadore.processengine.core.domain.event.ProcessStartedEvent;
import uz.salvadore.processengine.core.domain.event.ProcessSuspendedEvent;
import uz.salvadore.processengine.core.domain.event.TaskCompletedEvent;
import uz.salvadore.processengine.core.domain.event.TokenMovedEvent;
import uz.salvadore.processengine.core.domain.event.TokenWaitingEvent;
import uz.salvadore.processengine.core.domain.model.CompensationBoundaryEvent;
import uz.salvadore.processengine.core.domain.model.ErrorBoundaryEvent;
import uz.salvadore.processengine.core.domain.model.FlowNode;
import uz.salvadore.processengine.core.domain.model.ProcessDefinition;
import uz.salvadore.processengine.core.domain.model.ProcessInstance;
import uz.salvadore.processengine.core.domain.model.SequenceFlow;
import uz.salvadore.processengine.core.domain.model.Token;
import uz.salvadore.processengine.core.engine.context.ExecutionContext;
import uz.salvadore.processengine.core.engine.eventsourcing.EventApplier;
import uz.salvadore.processengine.core.engine.eventsourcing.ProcessInstanceProjection;
import uz.salvadore.processengine.core.port.outgoing.DeploymentListener;
import uz.salvadore.processengine.core.port.outgoing.InstanceDefinitionMapping;
import uz.salvadore.processengine.core.port.outgoing.ProcessDefinitionStore;
import uz.salvadore.processengine.core.port.outgoing.ProcessEventStore;
import uz.salvadore.processengine.core.port.outgoing.SequenceGenerator;
import uz.salvadore.processengine.core.util.UUIDv7;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Main facade for the process engine. Orchestrates process lifecycle:
 * deploy, start, complete tasks, fail tasks, suspend/resume, terminate.
 * Supports error boundary routing and compensation triggering on task failure.
 */
public final class ProcessEngine {

    private final ProcessEventStore eventStore;
    private final ProcessDefinitionStore definitionStore;
    private final TokenExecutor tokenExecutor;
    private final SequenceGenerator sequenceGenerator;
    private final InstanceDefinitionMapping instanceDefinitionMapping;
    private final EventApplier eventApplier;
    private final ProcessInstanceProjection projection;
    private final List<DeploymentListener> deploymentListeners;

    public ProcessEngine(ProcessEventStore eventStore,
                         ProcessDefinitionStore definitionStore,
                         TokenExecutor tokenExecutor,
                         SequenceGenerator sequenceGenerator,
                         InstanceDefinitionMapping instanceDefinitionMapping) {
        this(eventStore, definitionStore, tokenExecutor, sequenceGenerator, instanceDefinitionMapping, List.of());
    }

    public ProcessEngine(ProcessEventStore eventStore,
                         ProcessDefinitionStore definitionStore,
                         TokenExecutor tokenExecutor,
                         SequenceGenerator sequenceGenerator,
                         InstanceDefinitionMapping instanceDefinitionMapping,
                         List<DeploymentListener> deploymentListeners) {
        this.eventStore = eventStore;
        this.definitionStore = definitionStore;
        this.tokenExecutor = tokenExecutor;
        this.sequenceGenerator = sequenceGenerator;
        this.instanceDefinitionMapping = instanceDefinitionMapping;
        this.eventApplier = new EventApplier();
        this.projection = new ProcessInstanceProjection(eventApplier);
        this.deploymentListeners = deploymentListeners;
    }

    public ProcessDefinition deploy(ProcessDefinition definition) {
        ProcessDefinition deployed = definitionStore.deploy(definition);
        for (DeploymentListener listener : deploymentListeners) {
            listener.onDeploy(deployed);
        }
        return deployed;
    }

    public ProcessInstance startProcess(String definitionKey, Map<String, Object> variables) {
        ProcessDefinition definition = definitionStore.getByKey(definitionKey)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Process definition not found: " + definitionKey));

        UUID processInstanceId = UUIDv7.generate();
        Map<String, Object> processVariables = variables != null ? variables : Map.of();

        ProcessStartedEvent startedEvent = new ProcessStartedEvent(
                UUIDv7.generate(),
                processInstanceId,
                definition.getId(),
                null,
                processVariables,
                Instant.now(),
                sequenceGenerator.next(processInstanceId)
        );

        List<ProcessEvent> allEvents = new ArrayList<>();
        allEvents.add(startedEvent);

        ProcessInstance instance = eventApplier.apply(startedEvent, null);
        instanceDefinitionMapping.put(processInstanceId, definition.getId());

        Token startToken = instance.getTokens().getFirst();
        FlowNode startNode = findStartEvent(definition);

        ExecutionContext context = new ExecutionContext(instance, definition);
        List<ProcessEvent> executionEvents = tokenExecutor.execute(startToken, startNode, context);
        allEvents.addAll(executionEvents);

        for (ProcessEvent event : executionEvents) {
            instance = eventApplier.apply(event, instance);
        }

        instance = advanceActiveTokens(instance, definition, allEvents);

        eventStore.appendAll(allEvents);
        return instance;
    }

    public ProcessInstance completeTask(UUID correlationId, Map<String, Object> result) {
        UUID processInstanceId = findProcessInstanceByTokenId(correlationId);
        ProcessDefinition definition = getDefinitionForInstance(processInstanceId);
        ProcessInstance instance = getProcessInstance(processInstanceId);

        Token token = instance.getTokens().stream()
                .filter(t -> t.getId().equals(correlationId))
                .filter(t -> t.getState() == TokenState.WAITING || t.getState() == TokenState.ACTIVE)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "No active/waiting token found with id: " + correlationId));

        List<ProcessEvent> allEvents = new ArrayList<>();

        TaskCompletedEvent completedEvent = new TaskCompletedEvent(
                UUIDv7.generate(),
                processInstanceId,
                token.getId(),
                token.getCurrentNodeId(),
                result,
                Instant.now(),
                sequenceGenerator.next(processInstanceId)
        );
        allEvents.add(completedEvent);
        instance = eventApplier.apply(completedEvent, instance);

        FlowNode currentNode = findNodeById(definition, token.getCurrentNodeId());
        List<SequenceFlow> outgoingFlows = definition.getSequenceFlows().stream()
                .filter(f -> f.sourceRef().equals(currentNode.id()))
                .toList();

        if (!outgoingFlows.isEmpty()) {
            SequenceFlow nextFlow = outgoingFlows.getFirst();
            TokenMovedEvent movedEvent = new TokenMovedEvent(
                    UUIDv7.generate(),
                    processInstanceId,
                    token.getId(),
                    currentNode.id(),
                    nextFlow.targetRef(),
                    Instant.now(),
                    sequenceGenerator.next(processInstanceId)
            );
            allEvents.add(movedEvent);
            instance = eventApplier.apply(movedEvent, instance);
        }

        instance = advanceActiveTokens(instance, definition, allEvents);

        eventStore.appendAll(allEvents);
        return instance;
    }

    public ProcessInstance failTask(UUID correlationId, String errorCode, String errorMessage) {
        UUID processInstanceId = findProcessInstanceByTokenId(correlationId);
        ProcessDefinition definition = getDefinitionForInstance(processInstanceId);
        ProcessInstance instance = getProcessInstance(processInstanceId);

        Token token = instance.getTokens().stream()
                .filter(t -> t.getId().equals(correlationId))
                .filter(t -> t.getState() == TokenState.WAITING || t.getState() == TokenState.ACTIVE)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "No active/waiting token found with id: " + correlationId));

        String currentNodeId = token.getCurrentNodeId();
        List<ProcessEvent> allEvents = new ArrayList<>();

        // Look for ErrorBoundaryEvent attached to the current task
        ErrorBoundaryEvent errorBoundary = findErrorBoundary(definition, currentNodeId, errorCode);

        if (errorBoundary != null) {
            // Route token through the error boundary flow
            TokenMovedEvent movedEvent = new TokenMovedEvent(
                    UUIDv7.generate(),
                    processInstanceId,
                    token.getId(),
                    currentNodeId,
                    errorBoundary.id(),
                    Instant.now(),
                    sequenceGenerator.next(processInstanceId)
            );
            allEvents.add(movedEvent);
            instance = eventApplier.apply(movedEvent, instance);

            ExecutionContext context = new ExecutionContext(instance, definition);
            List<ProcessEvent> boundaryEvents = tokenExecutor.execute(
                    Token.restore(token.getId(), errorBoundary.id(), TokenState.ACTIVE),
                    errorBoundary, context);
            allEvents.addAll(boundaryEvents);
            for (ProcessEvent event : boundaryEvents) {
                instance = eventApplier.apply(event, instance);
            }

            instance = advanceActiveTokens(instance, definition, allEvents);
        } else {
            // Mark the failed token as COMPLETED so it won't be re-executed after event replay
            TaskCompletedEvent failedTokenCompleted = new TaskCompletedEvent(
                    UUIDv7.generate(),
                    processInstanceId,
                    token.getId(),
                    currentNodeId,
                    null,
                    Instant.now(),
                    sequenceGenerator.next(processInstanceId)
            );
            allEvents.add(failedTokenCompleted);
            instance = eventApplier.apply(failedTokenCompleted, instance);

            // No error boundary — trigger compensation for completed tasks, then error
            instance = triggerCompensation(instance, definition, allEvents);

            ProcessErrorEvent processError = new ProcessErrorEvent(
                    UUIDv7.generate(),
                    processInstanceId,
                    errorCode,
                    errorMessage,
                    Instant.now(),
                    sequenceGenerator.next(processInstanceId)
            );
            allEvents.add(processError);
            instance = eventApplier.apply(processError, instance);
        }

        eventStore.appendAll(allEvents);
        return instance;
    }

    public ProcessInstance completeCallActivity(UUID childInstanceId) {
        for (UUID parentInstanceId : instanceDefinitionMapping.allInstanceIds()) {
            ProcessInstance parentInstance = getProcessInstance(parentInstanceId);
            ProcessDefinition definition = getDefinitionForInstance(parentInstanceId);

            for (Token token : parentInstance.getTokens()) {
                if (token.getState() == TokenState.WAITING) {
                    FlowNode node = findNodeById(definition, token.getCurrentNodeId());
                    if (node.type() == NodeType.CALL_ACTIVITY) {
                        List<ProcessEvent> allEvents = new ArrayList<>();

                        CallActivityCompletedEvent completedEvent = new CallActivityCompletedEvent(
                                UUIDv7.generate(),
                                parentInstanceId,
                                token.getId(),
                                token.getCurrentNodeId(),
                                childInstanceId,
                                Instant.now(),
                                sequenceGenerator.next(parentInstanceId)
                        );
                        allEvents.add(completedEvent);
                        parentInstance = eventApplier.apply(completedEvent, parentInstance);

                        List<SequenceFlow> outgoingFlows = definition.getSequenceFlows().stream()
                                .filter(f -> f.sourceRef().equals(node.id()))
                                .toList();

                        if (!outgoingFlows.isEmpty()) {
                            TokenMovedEvent movedEvent = new TokenMovedEvent(
                                    UUIDv7.generate(),
                                    parentInstanceId,
                                    token.getId(),
                                    node.id(),
                                    outgoingFlows.getFirst().targetRef(),
                                    Instant.now(),
                                    sequenceGenerator.next(parentInstanceId)
                            );
                            allEvents.add(movedEvent);
                            parentInstance = eventApplier.apply(movedEvent, parentInstance);
                        }

                        parentInstance = advanceActiveTokens(parentInstance, definition, allEvents);
                        eventStore.appendAll(allEvents);
                        return parentInstance;
                    }
                }
            }
        }
        throw new IllegalStateException("No parent process found waiting for child: " + childInstanceId);
    }

    public ProcessInstance suspendProcess(UUID processInstanceId) {
        ProcessInstance instance = getProcessInstance(processInstanceId);
        if (instance.getState() != ProcessState.RUNNING) {
            throw new IllegalStateException("Cannot suspend process in state " + instance.getState());
        }

        ProcessSuspendedEvent event = new ProcessSuspendedEvent(
                UUIDv7.generate(),
                processInstanceId,
                Instant.now(),
                sequenceGenerator.next(processInstanceId)
        );

        eventStore.append(event);
        return eventApplier.apply(event, instance);
    }

    public ProcessInstance resumeProcess(UUID processInstanceId) {
        ProcessInstance instance = getProcessInstance(processInstanceId);
        if (instance.getState() != ProcessState.SUSPENDED) {
            throw new IllegalStateException("Cannot resume process in state " + instance.getState());
        }

        ProcessResumedEvent event = new ProcessResumedEvent(
                UUIDv7.generate(),
                processInstanceId,
                Instant.now(),
                sequenceGenerator.next(processInstanceId)
        );

        eventStore.append(event);
        return eventApplier.apply(event, instance);
    }

    public ProcessInstance terminateProcess(UUID processInstanceId) {
        ProcessInstance instance = getProcessInstance(processInstanceId);
        if (instance.getState() == ProcessState.COMPLETED || instance.getState() == ProcessState.TERMINATED) {
            throw new IllegalStateException("Cannot terminate process in state " + instance.getState());
        }

        ProcessErrorEvent event = new ProcessErrorEvent(
                UUIDv7.generate(),
                processInstanceId,
                "TERMINATED",
                "Process terminated by user",
                Instant.now(),
                sequenceGenerator.next(processInstanceId)
        );

        eventStore.append(event);
        return eventApplier.apply(event, instance);
    }

    public ProcessInstance getProcessInstance(UUID processInstanceId) {
        List<ProcessEvent> events = eventStore.getEvents(processInstanceId);
        if (events.isEmpty()) {
            throw new IllegalStateException("Process instance not found: " + processInstanceId);
        }
        return projection.replay(events);
    }

    public void sendMessage(UUID correlationId, Map<String, Object> payload) {
        completeTask(correlationId, payload);
    }

    private ProcessInstance advanceActiveTokens(ProcessInstance instance,
                                                 ProcessDefinition definition,
                                                 List<ProcessEvent> allEvents) {
        boolean advanced = true;
        while (advanced) {
            advanced = false;
            for (Token token : instance.getTokens()) {
                if (token.getState() != TokenState.ACTIVE) {
                    continue;
                }
                FlowNode node = findNodeById(definition, token.getCurrentNodeId());
                if (node.type() == NodeType.START_EVENT) {
                    continue;
                }

                if (node.type() == NodeType.SERVICE_TASK || node.type() == NodeType.CALL_ACTIVITY) {
                    ExecutionContext context = new ExecutionContext(instance, definition);
                    List<ProcessEvent> events = tokenExecutor.execute(token, node, context);
                    if (!events.isEmpty()) {
                        allEvents.addAll(events);
                        for (ProcessEvent event : events) {
                            instance = eventApplier.apply(event, instance);
                        }
                    } else {
                        TokenWaitingEvent waitingEvent = new TokenWaitingEvent(
                                UUIDv7.generate(),
                                instance.getId(),
                                token.getId(),
                                token.getCurrentNodeId(),
                                Instant.now(),
                                sequenceGenerator.next(instance.getId())
                        );
                        allEvents.add(waitingEvent);
                        instance = eventApplier.apply(waitingEvent, instance);
                    }
                    advanced = true;
                    break;
                }

                ExecutionContext context = new ExecutionContext(instance, definition);
                List<ProcessEvent> events = tokenExecutor.execute(token, node, context);

                if (!events.isEmpty()) {
                    allEvents.addAll(events);
                    for (ProcessEvent event : events) {
                        instance = eventApplier.apply(event, instance);
                    }
                    advanced = true;
                    break;
                }
            }
        }
        return instance;
    }

    private ErrorBoundaryEvent findErrorBoundary(ProcessDefinition definition,
                                                     String attachedToNodeId,
                                                     String errorCode) {
        return definition.getFlowNodes().stream()
                .filter(ErrorBoundaryEvent.class::isInstance)
                .map(ErrorBoundaryEvent.class::cast)
                .filter(e -> e.attachedToRef().equals(attachedToNodeId))
                .filter(e -> e.errorCode() == null || e.errorCode().equals(errorCode))
                .findFirst()
                .orElse(null);
    }

    private ProcessInstance triggerCompensation(ProcessInstance instance,
                                                ProcessDefinition definition,
                                                List<ProcessEvent> allEvents) {
        List<ProcessEvent> historicEvents = eventStore.getEvents(instance.getId());
        List<String> completedTaskNodeIds = new ArrayList<>();
        for (ProcessEvent event : historicEvents) {
            if (event instanceof TaskCompletedEvent taskCompleted) {
                completedTaskNodeIds.add(taskCompleted.nodeId());
            }
        }

        for (ProcessEvent event : allEvents) {
            if (event instanceof TaskCompletedEvent taskCompleted) {
                completedTaskNodeIds.add(taskCompleted.nodeId());
            }
        }

        List<CompensationBoundaryEvent> compensationEvents = new ArrayList<>();
        for (String nodeId : completedTaskNodeIds) {
            definition.getFlowNodes().stream()
                    .filter(CompensationBoundaryEvent.class::isInstance)
                    .map(CompensationBoundaryEvent.class::cast)
                    .filter(c -> c.attachedToRef().equals(nodeId))
                    .findFirst()
                    .ifPresent(compensationEvents::add);
        }

        List<CompensationBoundaryEvent> reversed = new ArrayList<>(compensationEvents);
        java.util.Collections.reverse(reversed);

        for (CompensationBoundaryEvent compensation : reversed) {
            List<SequenceFlow> outgoingFlows = definition.getSequenceFlows().stream()
                    .filter(f -> f.sourceRef().equals(compensation.id()))
                    .toList();

            String compensationTaskId = outgoingFlows.isEmpty()
                    ? null
                    : outgoingFlows.getFirst().targetRef();

            CompensationTriggeredEvent triggeredEvent = new CompensationTriggeredEvent(
                    UUIDv7.generate(),
                    instance.getId(),
                    compensation.attachedToRef(),
                    compensationTaskId,
                    Instant.now(),
                    sequenceGenerator.next(instance.getId())
            );
            allEvents.add(triggeredEvent);
            instance = eventApplier.apply(triggeredEvent, instance);

            if (compensationTaskId != null) {
                FlowNode compensationTask = findNodeById(definition, compensationTaskId);
                Token compensationToken = Token.create(compensationTaskId);

                TokenMovedEvent tokenEvent = new TokenMovedEvent(
                        UUIDv7.generate(),
                        instance.getId(),
                        compensationToken.getId(),
                        null,
                        compensationTaskId,
                        Instant.now(),
                        sequenceGenerator.next(instance.getId())
                );
                allEvents.add(tokenEvent);
                instance = eventApplier.apply(tokenEvent, instance);

                ExecutionContext context = new ExecutionContext(instance, definition);
                List<ProcessEvent> taskEvents = tokenExecutor.execute(compensationToken, compensationTask, context);
                allEvents.addAll(taskEvents);
                for (ProcessEvent event : taskEvents) {
                    instance = eventApplier.apply(event, instance);
                }

                TokenWaitingEvent waitingEvent = new TokenWaitingEvent(
                        UUIDv7.generate(),
                        instance.getId(),
                        compensationToken.getId(),
                        compensationTaskId,
                        Instant.now(),
                        sequenceGenerator.next(instance.getId())
                );
                allEvents.add(waitingEvent);
                instance = eventApplier.apply(waitingEvent, instance);
            }
        }

        return instance;
    }

    private FlowNode findStartEvent(ProcessDefinition definition) {
        return definition.getFlowNodes().stream()
                .filter(node -> node.type() == NodeType.START_EVENT)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No StartEvent found in definition"));
    }

    private FlowNode findNodeById(ProcessDefinition definition, String nodeId) {
        return definition.getFlowNodes().stream()
                .filter(node -> node.id().equals(nodeId))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Node not found: " + nodeId));
    }

    private UUID findProcessInstanceByTokenId(UUID tokenId) {
        for (UUID instanceId : instanceDefinitionMapping.allInstanceIds()) {
            List<ProcessEvent> events = eventStore.getEvents(instanceId);
            if (!events.isEmpty()) {
                ProcessInstance instance = projection.replay(events);
                boolean hasToken = instance.getTokens().stream()
                        .anyMatch(t -> t.getId().equals(tokenId));
                if (hasToken) {
                    return instanceId;
                }
            }
        }
        throw new IllegalStateException("No process instance found for token: " + tokenId);
    }

    private ProcessDefinition getDefinitionForInstance(UUID processInstanceId) {
        UUID definitionId = instanceDefinitionMapping.get(processInstanceId);
        if (definitionId == null) {
            throw new IllegalStateException("Definition not found for instance: " + processInstanceId);
        }
        return definitionStore.getById(definitionId)
                .orElseThrow(() -> new IllegalStateException(
                        "Process definition not found: " + definitionId));
    }
}
