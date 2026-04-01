package uz.salvadore.processengine.worker;

import java.util.Collections;
import java.util.Map;

/**
 * Provides the context for a task execution, including the correlation identifier,
 * all variables passed from the process engine, and methods to complete or fail the task.
 */
public class TaskContext {

    private final String correlationId;
    private final Map<String, Object> variables;
    private final ResponseSender responseSender;

    private volatile boolean responded = false;

    public TaskContext(String correlationId,
                       Map<String, Object> variables,
                       ResponseSender responseSender) {
        this.correlationId = correlationId;
        this.variables = variables != null ? Collections.unmodifiableMap(variables) : Collections.emptyMap();
        this.responseSender = responseSender;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public Map<String, Object> getVariables() {
        return variables;
    }

    public Object getVariable(String name) {
        return variables.get(name);
    }

    /**
     * Completes the task with the given result variables.
     * These variables will be merged into the process instance.
     */
    public void complete(Map<String, Object> result) {
        ensureNotResponded();
        responseSender.sendSuccess(correlationId, result != null ? result : Map.of());
        responded = true;
    }

    /**
     * Fails the task with the given error code and message.
     */
    public void error(String errorCode, String errorMessage) {
        ensureNotResponded();
        responseSender.sendError(correlationId, errorCode, errorMessage);
        responded = true;
    }

    public boolean isResponded() {
        return responded;
    }

    private void ensureNotResponded() {
        if (responded) {
            throw new IllegalStateException("Task already responded for correlationId: " + correlationId);
        }
    }

    /**
     * Callback interface for sending responses back to the process engine.
     */
    public interface ResponseSender {

        void sendSuccess(String correlationId, Map<String, Object> result);

        void sendError(String correlationId, String errorCode, String errorMessage);
    }
}
