package uz.salvadore.processengine.core.engine;

import uz.salvadore.processengine.core.domain.exception.CallActivitySubprocessNotFoundException;
import uz.salvadore.processengine.core.domain.model.CallActivity;
import uz.salvadore.processengine.core.domain.model.DeploymentBundle;
import uz.salvadore.processengine.core.domain.model.FlowNode;
import uz.salvadore.processengine.core.domain.model.ProcessDefinition;
import uz.salvadore.processengine.core.parser.BpmnParser;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class CallActivityValidator {

    private final BpmnParser bpmnParser;

    public CallActivityValidator(BpmnParser bpmnParser) {
        this.bpmnParser = bpmnParser;
    }

    public List<String> extractCalledElements(ProcessDefinition definition) {
        return definition.getFlowNodes().stream()
                .filter(CallActivity.class::isInstance)
                .map(CallActivity.class::cast)
                .map(CallActivity::calledElement)
                .toList();
    }

    public void validate(ProcessDefinition definition, DeploymentBundle bundle) {
        validate(definition, bundle, new HashSet<>());
    }

    private void validate(ProcessDefinition definition, DeploymentBundle bundle,
                           Set<String> visitedKeys) {
        String currentKey = definition.getKey();
        if (visitedKeys.contains(currentKey)) {
            throw new IllegalStateException(
                    "Cyclic Call Activity dependency detected: " + currentKey
                            + " is already in the call chain " + visitedKeys);
        }
        visitedKeys.add(currentKey);

        List<String> calledElements = extractCalledElements(definition);
        for (String calledElement : calledElements) {
            String expectedFileName = calledElement + ".bpmn";
            if (!bundle.containsFile(expectedFileName)) {
                throw new CallActivitySubprocessNotFoundException(
                        calledElement, expectedFileName, currentKey);
            }

            String subprocessXml = bundle.getBpmnFiles().get(expectedFileName);
            List<ProcessDefinition> subDefinitions = bpmnParser.parse(subprocessXml);
            for (ProcessDefinition subDefinition : subDefinitions) {
                validate(subDefinition, bundle, new HashSet<>(visitedKeys));
            }
        }
    }
}
