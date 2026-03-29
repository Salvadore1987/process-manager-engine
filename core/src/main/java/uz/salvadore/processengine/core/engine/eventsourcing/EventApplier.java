package uz.salvadore.processengine.core.engine.eventsourcing;

import uz.salvadore.processengine.core.domain.enums.ProcessState;
import uz.salvadore.processengine.core.domain.enums.TokenState;
import uz.salvadore.processengine.core.domain.event.CallActivityCompletedEvent;
import uz.salvadore.processengine.core.domain.event.CallActivityStartedEvent;
import uz.salvadore.processengine.core.domain.event.CompensationTriggeredEvent;
import uz.salvadore.processengine.core.domain.event.ProcessCompletedEvent;
import uz.salvadore.processengine.core.domain.event.ProcessErrorEvent;
import uz.salvadore.processengine.core.domain.event.ProcessEvent;
import uz.salvadore.processengine.core.domain.event.ProcessResumedEvent;
import uz.salvadore.processengine.core.domain.event.ProcessStartedEvent;
import uz.salvadore.processengine.core.domain.event.ProcessSuspendedEvent;
import uz.salvadore.processengine.core.domain.event.TaskCompletedEvent;
import uz.salvadore.processengine.core.domain.event.TimerFiredEvent;
import uz.salvadore.processengine.core.domain.event.TimerScheduledEvent;
import uz.salvadore.processengine.core.domain.event.TokenMovedEvent;
import uz.salvadore.processengine.core.domain.model.ProcessInstance;
import uz.salvadore.processengine.core.domain.model.Token;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Applies a ProcessEvent to a ProcessInstance, producing a new immutable state.
 * Uses Java 21 pattern matching switch on sealed ProcessEvent interface.
 */
public final class EventApplier {

    public ProcessInstance apply(ProcessEvent event, ProcessInstance instance) {
        return switch (event) {
            case ProcessStartedEvent e -> applyProcessStarted(e);
            case TokenMovedEvent e -> applyTokenMoved(e, instance);
            case TaskCompletedEvent e -> applyTaskCompleted(e, instance);
            case ProcessSuspendedEvent e -> applySuspended(instance);
            case ProcessResumedEvent e -> applyResumed(instance);
            case ProcessCompletedEvent e -> applyCompleted(instance);
            case ProcessErrorEvent e -> applyError(instance);
            case TimerScheduledEvent e -> instance;
            case TimerFiredEvent e -> applyTimerFired(e, instance);
            case CompensationTriggeredEvent e -> instance;
            case CallActivityStartedEvent e -> applyCallActivityStarted(e, instance);
            case CallActivityCompletedEvent e -> applyCallActivityCompleted(e, instance);
        };
    }

    private ProcessInstance applyProcessStarted(ProcessStartedEvent event) {
        Token initialToken = Token.create("__start__");
        return ProcessInstance.restore(
                event.processInstanceId(),
                event.definitionId(),
                event.parentProcessInstanceId(),
                ProcessState.RUNNING,
                List.of(initialToken),
                event.variables() != null ? event.variables() : Map.of(),
                event.occurredAt(),
                null
        );
    }

    private ProcessInstance applyTokenMoved(TokenMovedEvent event, ProcessInstance instance) {
        List<Token> updatedTokens = new ArrayList<>();
        boolean found = false;
        for (Token token : instance.getTokens()) {
            if (token.getId().equals(event.tokenId())) {
                updatedTokens.add(Token.restore(token.getId(), event.toNodeId(), TokenState.ACTIVE));
                found = true;
            } else {
                updatedTokens.add(token);
            }
        }
        if (!found) {
            // Token was created by a handler (e.g. ParallelGateway fork) or is the initial
            // token whose ID was not persisted in ProcessStartedEvent.
            // Replace the __start__ placeholder token if present, otherwise add new.
            boolean replacedPlaceholder = false;
            if (event.fromNodeId() != null) {
                for (int i = 0; i < updatedTokens.size(); i++) {
                    Token t = updatedTokens.get(i);
                    if ("__start__".equals(t.getCurrentNodeId()) && t.getState() == TokenState.ACTIVE) {
                        updatedTokens.set(i, Token.restore(event.tokenId(), event.toNodeId(), TokenState.ACTIVE));
                        replacedPlaceholder = true;
                        break;
                    }
                }
            }
            if (!replacedPlaceholder) {
                updatedTokens.add(Token.restore(event.tokenId(), event.toNodeId(), TokenState.ACTIVE));
            }
        }
        return instance.withTokens(updatedTokens);
    }

    private ProcessInstance applyTaskCompleted(TaskCompletedEvent event, ProcessInstance instance) {
        List<Token> updatedTokens = new ArrayList<>();
        for (Token token : instance.getTokens()) {
            if (token.getId().equals(event.tokenId())) {
                updatedTokens.add(Token.restore(token.getId(), event.nodeId(), TokenState.COMPLETED));
            } else {
                updatedTokens.add(token);
            }
        }
        Map<String, Object> mergedVariables = new HashMap<>(instance.getVariables());
        if (event.result() != null) {
            mergedVariables.putAll(event.result());
        }
        return instance.withTokens(updatedTokens).withVariables(mergedVariables);
    }

    private ProcessInstance applySuspended(ProcessInstance instance) {
        return ProcessInstance.restore(
                instance.getId(),
                instance.getDefinitionId(),
                instance.getParentProcessInstanceId(),
                ProcessState.SUSPENDED,
                instance.getTokens(),
                instance.getVariables(),
                instance.getStartedAt(),
                instance.getCompletedAt()
        );
    }

    private ProcessInstance applyResumed(ProcessInstance instance) {
        return ProcessInstance.restore(
                instance.getId(),
                instance.getDefinitionId(),
                instance.getParentProcessInstanceId(),
                ProcessState.RUNNING,
                instance.getTokens(),
                instance.getVariables(),
                instance.getStartedAt(),
                instance.getCompletedAt()
        );
    }

    private ProcessInstance applyCompleted(ProcessInstance instance) {
        return ProcessInstance.restore(
                instance.getId(),
                instance.getDefinitionId(),
                instance.getParentProcessInstanceId(),
                ProcessState.COMPLETED,
                instance.getTokens(),
                instance.getVariables(),
                instance.getStartedAt(),
                instance.getCompletedAt()
        );
    }

    private ProcessInstance applyError(ProcessInstance instance) {
        return ProcessInstance.restore(
                instance.getId(),
                instance.getDefinitionId(),
                instance.getParentProcessInstanceId(),
                ProcessState.ERROR,
                instance.getTokens(),
                instance.getVariables(),
                instance.getStartedAt(),
                instance.getCompletedAt()
        );
    }

    private ProcessInstance applyTimerFired(TimerFiredEvent event, ProcessInstance instance) {
        List<Token> updatedTokens = new ArrayList<>();
        for (Token token : instance.getTokens()) {
            if (token.getId().equals(event.tokenId())) {
                updatedTokens.add(Token.restore(token.getId(), event.nodeId(), TokenState.ACTIVE));
            } else {
                updatedTokens.add(token);
            }
        }
        return instance.withTokens(updatedTokens);
    }

    private ProcessInstance applyCallActivityStarted(CallActivityStartedEvent event, ProcessInstance instance) {
        List<Token> updatedTokens = new ArrayList<>();
        for (Token token : instance.getTokens()) {
            if (token.getId().equals(event.tokenId())) {
                updatedTokens.add(Token.restore(token.getId(), event.nodeId(), TokenState.WAITING));
            } else {
                updatedTokens.add(token);
            }
        }
        return instance.withTokens(updatedTokens);
    }

    private ProcessInstance applyCallActivityCompleted(CallActivityCompletedEvent event, ProcessInstance instance) {
        List<Token> updatedTokens = new ArrayList<>();
        for (Token token : instance.getTokens()) {
            if (token.getId().equals(event.tokenId())) {
                updatedTokens.add(Token.restore(token.getId(), event.nodeId(), TokenState.COMPLETED));
            } else {
                updatedTokens.add(token);
            }
        }
        return instance.withTokens(updatedTokens);
    }
}
