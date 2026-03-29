package uz.salvadore.processengine.core.engine;

import uz.salvadore.processengine.core.domain.enums.NodeType;
import uz.salvadore.processengine.core.domain.enums.ProcessState;
import uz.salvadore.processengine.core.domain.enums.TokenState;
import uz.salvadore.processengine.core.domain.event.CallActivityCompletedEvent;
import uz.salvadore.processengine.core.domain.event.ProcessEvent;
import uz.salvadore.processengine.core.domain.event.ProcessResumedEvent;
import uz.salvadore.processengine.core.domain.event.ProcessStartedEvent;
import uz.salvadore.processengine.core.domain.event.ProcessSuspendedEvent;
import uz.salvadore.processengine.core.domain.event.TaskCompletedEvent;
import uz.salvadore.processengine.core.domain.event.TokenMovedEvent;
import uz.salvadore.processengine.core.domain.model.FlowNode;
import uz.salvadore.processengine.core.domain.model.ProcessDefinition;
import uz.salvadore.processengine.core.domain.model.ProcessInstance;
import uz.salvadore.processengine.core.domain.model.SequenceFlow;
import uz.salvadore.processengine.core.domain.model.Token;
import uz.salvadore.processengine.core.engine.context.ExecutionContext;
import uz.salvadore.processengine.core.engine.eventsourcing.EventApplier;
import uz.salvadore.processengine.core.engine.eventsourcing.EventSequencer;
import uz.salvadore.processengine.core.engine.eventsourcing.ProcessInstanceProjection;
import uz.salvadore.processengine.core.port.outgoing.ProcessEventStore;
import uz.salvadore.processengine.core.util.UUIDv7;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Main facade for the process engine. Orchestrates process lifecycle:
 * deploy, start, complete tasks, suspend/resume, terminate.
 */
public final class ProcessEngine {

    private final ProcessEventStore eventStore;
    private final ProcessDefinitionRepository definitionRepository;
    private final TokenExecutor tokenExecutor;
    private final EventSequencer eventSequencer;
    private final EventApplier eventApplier;
    private final ProcessInstanceProjection projection;
    private final ConcurrentHashMap<UUID, UUID> instanceDefinitionMap = new ConcurrentHashMap<>();

    public ProcessEngine(ProcessEventStore eventStore,
                         ProcessDefinitionRepository definitionRepository,
                         TokenExecutor tokenExecutor,
                         EventSequencer eventSequencer) {
        this.eventStore = eventStore;
        this.definitionRepository = definitionRepository;
        this.tokenExecutor = tokenExecutor;
        this.eventSequencer = eventSequencer;
        this.eventApplier = new EventApplier();
        this.projection = new ProcessInstanceProjection(eventApplier);
    }

    public void deploy(ProcessDefinition definition) {
        definitionRepository.deploy(definition);
    }

    public ProcessInstance startProcess(String definitionKey, Map<String, Object> variables) {
        ProcessDefinition definition = definitionRepository.getByKey(definitionKey)
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
                eventSequencer.next(processInstanceId)
        );

        List<ProcessEvent> allEvents = new ArrayList<>();
        allEvents.add(startedEvent);

        ProcessInstance instance = eventApplier.apply(startedEvent, null);
        instanceDefinitionMap.put(processInstanceId, definition.getId());

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
                eventSequencer.next(processInstanceId)
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
                    eventSequencer.next(processInstanceId)
            );
            allEvents.add(movedEvent);
            instance = eventApplier.apply(movedEvent, instance);
        }

        instance = advanceActiveTokens(instance, definition, allEvents);

        eventStore.appendAll(allEvents);
        return instance;
    }

    public ProcessInstance completeCallActivity(UUID childInstanceId) {
        for (Map.Entry<UUID, UUID> entry : instanceDefinitionMap.entrySet()) {
            UUID parentInstanceId = entry.getKey();
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
                                eventSequencer.next(parentInstanceId)
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
                                    eventSequencer.next(parentInstanceId)
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
                eventSequencer.next(processInstanceId)
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
                eventSequencer.next(processInstanceId)
        );

        eventStore.append(event);
        return eventApplier.apply(event, instance);
    }

    public ProcessInstance terminateProcess(UUID processInstanceId) {
        ProcessInstance instance = getProcessInstance(processInstanceId);
        if (instance.getState() == ProcessState.COMPLETED || instance.getState() == ProcessState.TERMINATED) {
            throw new IllegalStateException("Cannot terminate process in state " + instance.getState());
        }

        uz.salvadore.processengine.core.domain.event.ProcessErrorEvent event =
                new uz.salvadore.processengine.core.domain.event.ProcessErrorEvent(
                        UUIDv7.generate(),
                        processInstanceId,
                        "TERMINATED",
                        "Process terminated by user",
                        Instant.now(),
                        eventSequencer.next(processInstanceId)
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

                // ServiceTask and CallActivity: execute handler (sends message / starts child),
                // then set the token to WAITING and stop advancing this token
                if (node.type() == NodeType.SERVICE_TASK || node.type() == NodeType.CALL_ACTIVITY) {
                    ExecutionContext context = new ExecutionContext(instance, definition);
                    List<ProcessEvent> events = tokenExecutor.execute(token, node, context);
                    if (!events.isEmpty()) {
                        allEvents.addAll(events);
                        for (ProcessEvent event : events) {
                            instance = eventApplier.apply(event, instance);
                        }
                    } else {
                        // ServiceTask returns empty events — set token to WAITING manually
                        List<Token> updatedTokens = new ArrayList<>(instance.getTokens());
                        for (int i = 0; i < updatedTokens.size(); i++) {
                            if (updatedTokens.get(i).getId().equals(token.getId())) {
                                updatedTokens.set(i, Token.restore(token.getId(), token.getCurrentNodeId(), TokenState.WAITING));
                                break;
                            }
                        }
                        instance = instance.withTokens(updatedTokens);
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
        for (UUID instanceId : instanceDefinitionMap.keySet()) {
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
        UUID definitionId = instanceDefinitionMap.get(processInstanceId);
        if (definitionId == null) {
            throw new IllegalStateException("Definition not found for instance: " + processInstanceId);
        }
        return definitionRepository.getById(definitionId)
                .orElseThrow(() -> new IllegalStateException(
                        "Process definition not found: " + definitionId));
    }
}
