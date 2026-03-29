package uz.salvadore.processengine.core.domain.model;

import uz.salvadore.processengine.core.domain.enums.ProcessState;
import uz.salvadore.processengine.core.util.UUIDv7;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ProcessInstance {

    private final UUID id;
    private final UUID definitionId;
    private final UUID parentProcessInstanceId;
    private final ProcessState state;
    private final List<Token> tokens;
    private final Map<String, Object> variables;
    private final Instant startedAt;
    private final Instant completedAt;

    private ProcessInstance(UUID id, UUID definitionId, UUID parentProcessInstanceId,
                            ProcessState state, List<Token> tokens, Map<String, Object> variables,
                            Instant startedAt, Instant completedAt) {
        this.id = id;
        this.definitionId = definitionId;
        this.parentProcessInstanceId = parentProcessInstanceId;
        this.state = state;
        this.tokens = List.copyOf(tokens);
        this.variables = Map.copyOf(variables);
        this.startedAt = startedAt;
        this.completedAt = completedAt;
    }

    public static ProcessInstance create(UUID definitionId, Map<String, Object> variables) {
        return new ProcessInstance(UUIDv7.generate(), definitionId, null,
                ProcessState.RUNNING, List.of(), variables, Instant.now(), null);
    }

    public static ProcessInstance createChild(UUID definitionId, UUID parentProcessInstanceId,
                                              Map<String, Object> variables) {
        return new ProcessInstance(UUIDv7.generate(), definitionId, parentProcessInstanceId,
                ProcessState.RUNNING, List.of(), variables, Instant.now(), null);
    }

    public static ProcessInstance restore(UUID id, UUID definitionId, UUID parentProcessInstanceId,
                                          ProcessState state, List<Token> tokens,
                                          Map<String, Object> variables,
                                          Instant startedAt, Instant completedAt) {
        return new ProcessInstance(id, definitionId, parentProcessInstanceId, state,
                tokens, variables, startedAt, completedAt);
    }

    public ProcessInstance withTokens(List<Token> newTokens) {
        return new ProcessInstance(id, definitionId, parentProcessInstanceId, state,
                newTokens, variables, startedAt, completedAt);
    }

    public ProcessInstance withVariables(Map<String, Object> newVariables) {
        return new ProcessInstance(id, definitionId, parentProcessInstanceId, state,
                tokens, newVariables, startedAt, completedAt);
    }

    public ProcessInstance suspend() {
        if (state != ProcessState.RUNNING) {
            throw new IllegalStateException("Cannot suspend process in state " + state);
        }
        return new ProcessInstance(id, definitionId, parentProcessInstanceId,
                ProcessState.SUSPENDED, tokens, variables, startedAt, completedAt);
    }

    public ProcessInstance resume() {
        if (state != ProcessState.SUSPENDED) {
            throw new IllegalStateException("Cannot resume process in state " + state);
        }
        return new ProcessInstance(id, definitionId, parentProcessInstanceId,
                ProcessState.RUNNING, tokens, variables, startedAt, completedAt);
    }

    public ProcessInstance complete() {
        if (state != ProcessState.RUNNING) {
            throw new IllegalStateException("Cannot complete process in state " + state);
        }
        return new ProcessInstance(id, definitionId, parentProcessInstanceId,
                ProcessState.COMPLETED, tokens, variables, startedAt, Instant.now());
    }

    public ProcessInstance error() {
        if (state != ProcessState.RUNNING) {
            throw new IllegalStateException("Cannot set error on process in state " + state);
        }
        return new ProcessInstance(id, definitionId, parentProcessInstanceId,
                ProcessState.ERROR, tokens, variables, startedAt, Instant.now());
    }

    public ProcessInstance terminate() {
        if (state == ProcessState.COMPLETED || state == ProcessState.TERMINATED) {
            throw new IllegalStateException("Cannot terminate process in state " + state);
        }
        return new ProcessInstance(id, definitionId, parentProcessInstanceId,
                ProcessState.TERMINATED, tokens, variables, startedAt, Instant.now());
    }

    public UUID getId() {
        return id;
    }

    public UUID getDefinitionId() {
        return definitionId;
    }

    public UUID getParentProcessInstanceId() {
        return parentProcessInstanceId;
    }

    public ProcessState getState() {
        return state;
    }

    public List<Token> getTokens() {
        return tokens;
    }

    public Map<String, Object> getVariables() {
        return variables;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }
}
