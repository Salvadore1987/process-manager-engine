package uz.salvadore.processengine.core.engine;

import uz.salvadore.processengine.core.domain.enums.NodeType;
import uz.salvadore.processengine.core.domain.enums.ProcessState;
import uz.salvadore.processengine.core.domain.enums.TokenState;
import uz.salvadore.processengine.core.domain.event.CallActivityCompletedEvent;
import uz.salvadore.processengine.core.domain.event.CallActivityStartedEvent;
import uz.salvadore.processengine.core.domain.event.CompensationTriggeredEvent;
import uz.salvadore.processengine.core.domain.event.ProcessErrorEvent;
import uz.salvadore.processengine.core.domain.event.ProcessEvent;
import uz.salvadore.processengine.core.domain.event.ProcessCompletedEvent;
import uz.salvadore.processengine.core.domain.event.ProcessResumedEvent;
import uz.salvadore.processengine.core.domain.event.ProcessStartedEvent;
import uz.salvadore.processengine.core.domain.event.ProcessSuspendedEvent;
import uz.salvadore.processengine.core.domain.event.TaskCompletedEvent;
import uz.salvadore.processengine.core.domain.event.TokenMovedEvent;
import uz.salvadore.processengine.core.domain.event.TimerFiredEvent;
import uz.salvadore.processengine.core.domain.event.TokenWaitingEvent;
import uz.salvadore.processengine.core.domain.model.CallActivity;
import uz.salvadore.processengine.core.domain.model.CompensationBoundaryEvent;
import uz.salvadore.processengine.core.domain.model.DeploymentBundle;
import uz.salvadore.processengine.core.domain.model.ErrorBoundaryEvent;
import uz.salvadore.processengine.core.domain.model.FlowNode;
import uz.salvadore.processengine.core.domain.model.ProcessDefinition;
import uz.salvadore.processengine.core.domain.model.ProcessInstance;
import uz.salvadore.processengine.core.domain.model.SequenceFlow;
import uz.salvadore.processengine.core.domain.model.ServiceTask;
import uz.salvadore.processengine.core.domain.model.TimerIntermediateCatchEvent;
import uz.salvadore.processengine.core.domain.model.Token;
import uz.salvadore.processengine.core.engine.context.ExecutionContext;
import uz.salvadore.processengine.core.engine.eventsourcing.EventApplier;
import uz.salvadore.processengine.core.engine.eventsourcing.ProcessInstanceProjection;
import uz.salvadore.processengine.core.parser.BpmnParser;
import uz.salvadore.processengine.core.port.outgoing.ActivityLog;
import uz.salvadore.processengine.core.port.outgoing.BusinessKeyMapping;
import uz.salvadore.processengine.core.port.outgoing.ChildInstanceMapping;
import uz.salvadore.processengine.core.port.outgoing.DeploymentListener;
import uz.salvadore.processengine.core.port.outgoing.InstanceDefinitionMapping;
import uz.salvadore.processengine.core.port.outgoing.ProcessDefinitionStore;
import uz.salvadore.processengine.core.port.outgoing.ProcessEventStore;
import uz.salvadore.processengine.core.port.outgoing.ProcessInstanceLock;
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
    private final ChildInstanceMapping childInstanceMapping;
    private final BusinessKeyMapping businessKeyMapping;
    private final ProcessInstanceLock processInstanceLock;
    private final ActivityLog activityLog;
    private final EventApplier eventApplier;
    private final ProcessInstanceProjection projection;
    private final List<DeploymentListener> deploymentListeners;
    private final BpmnParser bpmnParser;

    public ProcessEngine(ProcessEventStore eventStore,
                         ProcessDefinitionStore definitionStore,
                         TokenExecutor tokenExecutor,
                         SequenceGenerator sequenceGenerator,
                         InstanceDefinitionMapping instanceDefinitionMapping,
                         ProcessInstanceLock processInstanceLock,
                         BusinessKeyMapping businessKeyMapping,
                         ActivityLog activityLog) {
        this(eventStore, definitionStore, tokenExecutor, sequenceGenerator,
                instanceDefinitionMapping, null, businessKeyMapping, processInstanceLock, activityLog, List.of());
    }

    public ProcessEngine(ProcessEventStore eventStore,
                         ProcessDefinitionStore definitionStore,
                         TokenExecutor tokenExecutor,
                         SequenceGenerator sequenceGenerator,
                         InstanceDefinitionMapping instanceDefinitionMapping,
                         ProcessInstanceLock processInstanceLock,
                         BusinessKeyMapping businessKeyMapping,
                         ActivityLog activityLog,
                         List<DeploymentListener> deploymentListeners) {
        this(eventStore, definitionStore, tokenExecutor, sequenceGenerator,
                instanceDefinitionMapping, null, businessKeyMapping, processInstanceLock, activityLog, deploymentListeners);
    }

    public ProcessEngine(ProcessEventStore eventStore,
                         ProcessDefinitionStore definitionStore,
                         TokenExecutor tokenExecutor,
                         SequenceGenerator sequenceGenerator,
                         InstanceDefinitionMapping instanceDefinitionMapping,
                         ChildInstanceMapping childInstanceMapping,
                         BusinessKeyMapping businessKeyMapping,
                         ProcessInstanceLock processInstanceLock,
                         ActivityLog activityLog,
                         List<DeploymentListener> deploymentListeners) {
        this.eventStore = eventStore;
        this.definitionStore = definitionStore;
        this.tokenExecutor = tokenExecutor;
        this.sequenceGenerator = sequenceGenerator;
        this.instanceDefinitionMapping = instanceDefinitionMapping;
        this.childInstanceMapping = childInstanceMapping;
        this.businessKeyMapping = businessKeyMapping;
        this.processInstanceLock = processInstanceLock;
        this.activityLog = activityLog;
        this.eventApplier = new EventApplier();
        this.projection = new ProcessInstanceProjection(eventApplier);
        this.deploymentListeners = deploymentListeners;
        this.bpmnParser = new BpmnParser();
    }

    public ProcessDefinition deploy(ProcessDefinition definition) {
        boolean hasCallActivity = definition.getFlowNodes().stream()
                .anyMatch(CallActivity.class::isInstance);
        if (hasCallActivity) {
            throw new IllegalArgumentException(
                    "Process definition '" + definition.getKey()
                            + "' contains Call Activity elements. Use deployBundle() instead.");
        }
        ProcessDefinition deployed = definitionStore.deploy(definition);
        for (DeploymentListener listener : deploymentListeners) {
            listener.onDeploy(deployed);
        }
        return deployed;
    }

    public List<ProcessDefinition> deployBundle(DeploymentBundle bundle) {
        CallActivityValidator validator = new CallActivityValidator(bpmnParser);

        Map<String, ProcessDefinition> parsedDefinitions = new java.util.LinkedHashMap<>();
        for (Map.Entry<String, String> entry : bundle.getBpmnFiles().entrySet()) {
            List<ProcessDefinition> definitions = bpmnParser.parse(entry.getValue());
            ProcessDefinition definition = definitions.getFirst();
            parsedDefinitions.put(entry.getKey(), definition);
        }

        ProcessDefinition mainDefinition = parsedDefinitions.values().iterator().next();
        validator.validate(mainDefinition, bundle);

        List<ProcessDefinition> deployedDefinitions = new ArrayList<>();
        try {
            for (ProcessDefinition definition : parsedDefinitions.values()) {
                ProcessDefinition deployed = definitionStore.deploy(definition);
                deployedDefinitions.add(deployed);
            }
            for (ProcessDefinition deployed : deployedDefinitions) {
                for (DeploymentListener listener : deploymentListeners) {
                    listener.onDeploy(deployed);
                }
            }
        } catch (Exception e) {
            for (ProcessDefinition deployed : deployedDefinitions) {
                definitionStore.undeploy(deployed.getKey());
            }
            throw e;
        }

        return deployedDefinitions;
    }

    public ProcessInstance startProcess(String definitionKey, String businessKey,
                                        Map<String, Object> variables) {
        if (businessKey == null || businessKey.isBlank()) {
            throw new IllegalArgumentException("Business key must not be null or blank");
        }
        if (businessKeyMapping.get(businessKey) != null) {
            throw new IllegalArgumentException("Business key already in use: " + businessKey);
        }

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
                businessKey,
                processVariables,
                Instant.now(),
                sequenceGenerator.next(processInstanceId)
        );

        List<ProcessEvent> allEvents = new ArrayList<>();
        allEvents.add(startedEvent);

        ProcessInstance instance = eventApplier.apply(startedEvent, null);
        instanceDefinitionMapping.put(processInstanceId, definition.getId());
        businessKeyMapping.put(businessKey, processInstanceId);

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

    public ProcessInstance getProcessInstanceByBusinessKey(String businessKey) {
        UUID processInstanceId = businessKeyMapping.get(businessKey);
        if (processInstanceId == null) {
            throw new IllegalStateException(
                    "Process instance not found for business key: " + businessKey);
        }
        return getProcessInstance(processInstanceId);
    }

    public ProcessInstance completeTask(UUID correlationId, Map<String, Object> result) {
        UUID processInstanceId = findProcessInstanceByTokenId(correlationId);
        processInstanceLock.lock(processInstanceId);
        try {
            ProcessDefinition definition = getDefinitionForInstance(processInstanceId);
            ProcessInstance instance = getProcessInstance(processInstanceId);

            Token token = instance.getTokens().stream()
                    .filter(t -> t.getId().equals(correlationId))
                    .filter(t -> t.getState() == TokenState.WAITING || t.getState() == TokenState.ACTIVE)
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException(
                            "No active/waiting token found with id: " + correlationId));

            List<ProcessEvent> allEvents = new ArrayList<>();

            Instant completedAt = Instant.now();
            TaskCompletedEvent completedEvent = new TaskCompletedEvent(
                    UUIDv7.generate(),
                    processInstanceId,
                    token.getId(),
                    token.getCurrentNodeId(),
                    result,
                    completedAt,
                    sequenceGenerator.next(processInstanceId)
            );
            allEvents.add(completedEvent);
            instance = eventApplier.apply(completedEvent, instance);
            activityLog.taskCompleted(processInstanceId, token.getCurrentNodeId(), result, completedAt);

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
        } finally {
            processInstanceLock.unlock(processInstanceId);
        }
    }

    public ProcessInstance failTask(UUID correlationId, String errorCode, String errorMessage) {
        UUID processInstanceId = findProcessInstanceByTokenId(correlationId);
        processInstanceLock.lock(processInstanceId);
        try {
            return doFailTask(processInstanceId, correlationId, errorCode, errorMessage);
        } finally {
            processInstanceLock.unlock(processInstanceId);
        }
    }

    private ProcessInstance doFailTask(UUID processInstanceId, UUID correlationId,
                                       String errorCode, String errorMessage) {
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
        activityLog.taskFailed(processInstanceId, currentNodeId, errorCode, errorMessage, Instant.now());

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

            Instant errorAt = Instant.now();
            ProcessErrorEvent processError = new ProcessErrorEvent(
                    UUIDv7.generate(),
                    processInstanceId,
                    errorCode,
                    errorMessage,
                    errorAt,
                    sequenceGenerator.next(processInstanceId)
            );
            allEvents.add(processError);
            instance = eventApplier.apply(processError, instance);
            activityLog.processErrored(processInstanceId, errorCode, errorMessage, errorAt);
        }

        eventStore.appendAll(allEvents);

        if (instance.getState() == ProcessState.ERROR) {
            propagateChildError(processInstanceId, errorCode, errorMessage);
        }

        return instance;
    }

    public ProcessInstance completeCallActivity(UUID childInstanceId) {
        for (UUID parentInstanceId : instanceDefinitionMapping.allInstanceIds()) {
            processInstanceLock.lock(parentInstanceId);
            try {
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
            } finally {
                processInstanceLock.unlock(parentInstanceId);
            }
        }
        throw new IllegalStateException("No parent process found waiting for child: " + childInstanceId);
    }

    public ProcessInstance suspendProcess(UUID processInstanceId) {
        processInstanceLock.lock(processInstanceId);
        try {
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
        } finally {
            processInstanceLock.unlock(processInstanceId);
        }
    }

    public ProcessInstance resumeProcess(UUID processInstanceId) {
        processInstanceLock.lock(processInstanceId);
        try {
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
        } finally {
            processInstanceLock.unlock(processInstanceId);
        }
    }

    public ProcessInstance terminateProcess(UUID processInstanceId) {
        processInstanceLock.lock(processInstanceId);
        try {
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
        } finally {
            processInstanceLock.unlock(processInstanceId);
        }
    }

    public ProcessInstance getProcessInstance(UUID processInstanceId) {
        List<ProcessEvent> events = eventStore.getEvents(processInstanceId);
        if (events.isEmpty()) {
            throw new IllegalStateException("Process instance not found: " + processInstanceId);
        }
        return projection.replay(events);
    }

    public ProcessInstance completeTimer(UUID processInstanceId, UUID tokenId, String nodeId) {
        processInstanceLock.lock(processInstanceId);
        try {
            ProcessDefinition definition = getDefinitionForInstance(processInstanceId);
            ProcessInstance instance = getProcessInstance(processInstanceId);

            Token token = instance.getTokens().stream()
                    .filter(t -> t.getId().equals(tokenId))
                    .filter(t -> t.getState() == TokenState.WAITING)
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException(
                            "No waiting token found with id: " + tokenId));

            List<ProcessEvent> allEvents = new ArrayList<>();

            TimerFiredEvent firedEvent = new TimerFiredEvent(
                    UUIDv7.generate(),
                    processInstanceId,
                    tokenId,
                    nodeId,
                    Instant.now(),
                    sequenceGenerator.next(processInstanceId)
            );
            allEvents.add(firedEvent);
            instance = eventApplier.apply(firedEvent, instance);

            FlowNode currentNode = findNodeById(definition, nodeId);
            List<SequenceFlow> outgoingFlows = definition.getSequenceFlows().stream()
                    .filter(f -> f.sourceRef().equals(nodeId))
                    .toList();

            if (!outgoingFlows.isEmpty()) {
                TokenMovedEvent movedEvent = new TokenMovedEvent(
                        UUIDv7.generate(),
                        processInstanceId,
                        tokenId,
                        nodeId,
                        outgoingFlows.getFirst().targetRef(),
                        Instant.now(),
                        sequenceGenerator.next(processInstanceId)
                );
                allEvents.add(movedEvent);
                instance = eventApplier.apply(movedEvent, instance);
            }

            instance = advanceActiveTokens(instance, definition, allEvents);

            eventStore.appendAll(allEvents);
            return instance;
        } finally {
            processInstanceLock.unlock(processInstanceId);
        }
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

                if (node.type() == NodeType.SERVICE_TASK || node.type() == NodeType.CALL_ACTIVITY
                        || node.type() == NodeType.TIMER_INTERMEDIATE_CATCH) {
                    // Flush accumulated events before sending message to RabbitMQ.
                    // ServiceTaskHandler.send() triggers external workers that may respond
                    // before eventStore.appendAll() in the caller — causing "instance not found".
                    if (!allEvents.isEmpty()) {
                        eventStore.appendAll(allEvents);
                        allEvents.clear();
                    }
                    String activityTopic = node instanceof ServiceTask serviceTask
                            ? serviceTask.topic()
                            : node instanceof CallActivity callActivity
                            ? callActivity.calledElement()
                            : node instanceof TimerIntermediateCatchEvent timerCatch
                            ? "timer:" + timerCatch.timerDefinition().value()
                            : node.id();
                    activityLog.taskStarted(instance.getId(), node.id(), activityTopic, Instant.now());
                    ExecutionContext context = new ExecutionContext(instance, definition);
                    List<ProcessEvent> events = tokenExecutor.execute(token, node, context);
                    if (!events.isEmpty()) {
                        allEvents.addAll(events);
                        for (ProcessEvent event : events) {
                            instance = eventApplier.apply(event, instance);
                            if (event instanceof CallActivityStartedEvent caEvent) {
                                autoStartChildProcess(caEvent, instance);
                            }
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
                        if (event instanceof ProcessCompletedEvent completedEvt) {
                            activityLog.processCompleted(completedEvt.processInstanceId(), completedEvt.occurredAt());
                            autoCompleteParent(completedEvt.processInstanceId());
                        }
                        if (event instanceof ProcessErrorEvent errorEvt) {
                            propagateChildError(errorEvt.processInstanceId(),
                                    errorEvt.errorCode(), errorEvt.errorMessage());
                        }
                    }
                    advanced = true;
                    break;
                }
            }
        }
        return instance;
    }

    private void autoStartChildProcess(CallActivityStartedEvent event, ProcessInstance parentInstance) {
        if (childInstanceMapping == null) {
            return;
        }

        String calledElement = event.calledElement();
        ProcessDefinition childDefinition = definitionStore.getByKey(calledElement)
                .orElseThrow(() -> new IllegalStateException(
                        "Subprocess definition not found: " + calledElement));

        UUID childProcessInstanceId = event.childProcessInstanceId();
        Map<String, Object> childVariables = new HashMap<>(parentInstance.getVariables());

        ProcessStartedEvent childStartedEvent = new ProcessStartedEvent(
                UUIDv7.generate(),
                childProcessInstanceId,
                childDefinition.getId(),
                parentInstance.getId(),
                null,
                childVariables,
                Instant.now(),
                sequenceGenerator.next(childProcessInstanceId)
        );

        List<ProcessEvent> childEvents = new ArrayList<>();
        childEvents.add(childStartedEvent);

        ProcessInstance childInstance = eventApplier.apply(childStartedEvent, null);
        instanceDefinitionMapping.put(childProcessInstanceId, childDefinition.getId());
        childInstanceMapping.put(childProcessInstanceId, parentInstance.getId());

        Token startToken = childInstance.getTokens().getFirst();
        FlowNode startNode = findStartEvent(childDefinition);

        ExecutionContext childContext = new ExecutionContext(childInstance, childDefinition);
        List<ProcessEvent> executionEvents = tokenExecutor.execute(startToken, startNode, childContext);
        childEvents.addAll(executionEvents);

        for (ProcessEvent childEvent : executionEvents) {
            childInstance = eventApplier.apply(childEvent, childInstance);
        }

        childInstance = advanceActiveTokens(childInstance, childDefinition, childEvents);

        eventStore.appendAll(childEvents);
    }

    private void autoCompleteParent(UUID childProcessInstanceId) {
        if (childInstanceMapping == null) {
            return;
        }

        UUID parentInstanceId = childInstanceMapping.getParent(childProcessInstanceId);
        if (parentInstanceId == null) {
            return;
        }

        ProcessInstance childInstance = getProcessInstance(childProcessInstanceId);
        Map<String, Object> childVariables = childInstance.getVariables();

        processInstanceLock.lock(parentInstanceId);
        try {
            ProcessDefinition parentDefinition = getDefinitionForInstance(parentInstanceId);
            ProcessInstance parentInstance = getProcessInstance(parentInstanceId);

            for (Token token : parentInstance.getTokens()) {
                if (token.getState() == TokenState.WAITING) {
                    FlowNode node = findNodeById(parentDefinition, token.getCurrentNodeId());
                    if (node.type() == NodeType.CALL_ACTIVITY) {
                        List<ProcessEvent> allEvents = new ArrayList<>();

                        CallActivityCompletedEvent completedEvent = new CallActivityCompletedEvent(
                                UUIDv7.generate(),
                                parentInstanceId,
                                token.getId(),
                                token.getCurrentNodeId(),
                                childProcessInstanceId,
                                Instant.now(),
                                sequenceGenerator.next(parentInstanceId)
                        );
                        allEvents.add(completedEvent);
                        parentInstance = eventApplier.apply(completedEvent, parentInstance);

                        Map<String, Object> mergedVariables = new HashMap<>(parentInstance.getVariables());
                        mergedVariables.putAll(childVariables);
                        parentInstance = parentInstance.withVariables(mergedVariables);

                        List<SequenceFlow> outgoingFlows = parentDefinition.getSequenceFlows().stream()
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

                        parentInstance = advanceActiveTokens(parentInstance, parentDefinition, allEvents);
                        eventStore.appendAll(allEvents);
                        return;
                    }
                }
            }
        } finally {
            processInstanceLock.unlock(parentInstanceId);
        }
    }

    public void propagateChildError(UUID childProcessInstanceId, String errorCode, String errorMessage) {
        if (childInstanceMapping == null) {
            return;
        }

        UUID parentInstanceId = childInstanceMapping.getParent(childProcessInstanceId);
        if (parentInstanceId == null) {
            return;
        }

        processInstanceLock.lock(parentInstanceId);
        try {
            ProcessDefinition parentDefinition = getDefinitionForInstance(parentInstanceId);
            ProcessInstance parentInstance = getProcessInstance(parentInstanceId);

            for (Token token : parentInstance.getTokens()) {
                if (token.getState() == TokenState.WAITING) {
                    FlowNode node = findNodeById(parentDefinition, token.getCurrentNodeId());
                    if (node.type() == NodeType.CALL_ACTIVITY) {
                        ErrorBoundaryEvent errorBoundary = findErrorBoundary(
                                parentDefinition, node.id(), errorCode);

                        List<ProcessEvent> allEvents = new ArrayList<>();

                        if (errorBoundary != null) {
                            TokenMovedEvent movedEvent = new TokenMovedEvent(
                                    UUIDv7.generate(),
                                    parentInstanceId,
                                    token.getId(),
                                    node.id(),
                                    errorBoundary.id(),
                                    Instant.now(),
                                    sequenceGenerator.next(parentInstanceId)
                            );
                            allEvents.add(movedEvent);
                            parentInstance = eventApplier.apply(movedEvent, parentInstance);

                            ExecutionContext context = new ExecutionContext(parentInstance, parentDefinition);
                            List<ProcessEvent> boundaryEvents = tokenExecutor.execute(
                                    Token.restore(token.getId(), errorBoundary.id(), TokenState.ACTIVE),
                                    errorBoundary, context);
                            allEvents.addAll(boundaryEvents);
                            for (ProcessEvent event : boundaryEvents) {
                                parentInstance = eventApplier.apply(event, parentInstance);
                            }

                            parentInstance = advanceActiveTokens(parentInstance, parentDefinition, allEvents);
                        } else {
                            TaskCompletedEvent failedTokenCompleted = new TaskCompletedEvent(
                                    UUIDv7.generate(),
                                    parentInstanceId,
                                    token.getId(),
                                    node.id(),
                                    null,
                                    Instant.now(),
                                    sequenceGenerator.next(parentInstanceId)
                            );
                            allEvents.add(failedTokenCompleted);
                            parentInstance = eventApplier.apply(failedTokenCompleted, parentInstance);

                            parentInstance = triggerCompensation(parentInstance, parentDefinition, allEvents);

                            ProcessErrorEvent processError = new ProcessErrorEvent(
                                    UUIDv7.generate(),
                                    parentInstanceId,
                                    errorCode,
                                    errorMessage,
                                    Instant.now(),
                                    sequenceGenerator.next(parentInstanceId)
                            );
                            allEvents.add(processError);
                            parentInstance = eventApplier.apply(processError, parentInstance);
                            activityLog.processErrored(parentInstanceId, errorCode, errorMessage, Instant.now());
                        }

                        eventStore.appendAll(allEvents);
                        return;
                    }
                }
            }
        } finally {
            processInstanceLock.unlock(parentInstanceId);
        }
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
