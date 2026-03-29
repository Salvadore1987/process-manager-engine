package uz.salvadore.processengine.core.engine.context;

import uz.salvadore.processengine.core.domain.event.ProcessEvent;
import uz.salvadore.processengine.core.domain.model.FlowNode;
import uz.salvadore.processengine.core.domain.model.ProcessDefinition;
import uz.salvadore.processengine.core.domain.model.ProcessInstance;
import uz.salvadore.processengine.core.domain.model.SequenceFlow;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Carries the current execution state during token processing.
 * Collects events produced by node handlers and provides access to
 * the process definition graph and instance state.
 */
public final class ExecutionContext {

    private final ProcessInstance processInstance;
    private final ProcessDefinition processDefinition;
    private final List<ProcessEvent> collectedEvents;
    private final Map<String, Object> variables;

    public ExecutionContext(ProcessInstance processInstance, ProcessDefinition processDefinition) {
        this.processInstance = processInstance;
        this.processDefinition = processDefinition;
        this.collectedEvents = new ArrayList<>();
        this.variables = new HashMap<>(processInstance.getVariables());
    }

    public ProcessInstance getProcessInstance() {
        return processInstance;
    }

    public ProcessDefinition getProcessDefinition() {
        return processDefinition;
    }

    public Map<String, Object> getVariables() {
        return Collections.unmodifiableMap(variables);
    }

    public void mergeVariables(Map<String, Object> newVariables) {
        if (newVariables != null) {
            variables.putAll(newVariables);
        }
    }

    public void addEvent(ProcessEvent event) {
        collectedEvents.add(event);
    }

    public void addEvents(List<ProcessEvent> events) {
        collectedEvents.addAll(events);
    }

    public List<ProcessEvent> getCollectedEvents() {
        return Collections.unmodifiableList(collectedEvents);
    }

    public Optional<FlowNode> findNodeById(String nodeId) {
        return processDefinition.getFlowNodes().stream()
                .filter(node -> node.id().equals(nodeId))
                .findFirst();
    }

    public Optional<SequenceFlow> findSequenceFlowById(String flowId) {
        return processDefinition.getSequenceFlows().stream()
                .filter(flow -> flow.id().equals(flowId))
                .findFirst();
    }

    public List<SequenceFlow> findOutgoingFlows(String nodeId) {
        return processDefinition.getSequenceFlows().stream()
                .filter(flow -> flow.sourceRef().equals(nodeId))
                .toList();
    }

    public List<SequenceFlow> findIncomingFlows(String nodeId) {
        return processDefinition.getSequenceFlows().stream()
                .filter(flow -> flow.targetRef().equals(nodeId))
                .toList();
    }

    public String resolveTargetNodeId(String flowId) {
        return findSequenceFlowById(flowId)
                .map(SequenceFlow::targetRef)
                .orElseThrow(() -> new IllegalStateException("Sequence flow not found: " + flowId));
    }
}
