package uz.salvadore.processengine.core.parser.mapper;

import uz.salvadore.processengine.core.domain.model.CallActivity;
import uz.salvadore.processengine.core.domain.model.CompensationBoundaryEvent;
import uz.salvadore.processengine.core.domain.model.EndEvent;
import uz.salvadore.processengine.core.domain.model.ErrorBoundaryEvent;
import uz.salvadore.processengine.core.domain.model.ExclusiveGateway;
import uz.salvadore.processengine.core.domain.model.FlowNode;
import uz.salvadore.processengine.core.domain.model.ParallelGateway;
import uz.salvadore.processengine.core.domain.model.ServiceTask;
import uz.salvadore.processengine.core.domain.model.StartEvent;
import uz.salvadore.processengine.core.domain.model.TimerBoundaryEvent;
import uz.salvadore.processengine.core.parser.BpmnParseException;
import uz.salvadore.processengine.core.parser.jaxb.BpmnBoundaryEvent;
import uz.salvadore.processengine.core.parser.jaxb.BpmnCallActivity;
import uz.salvadore.processengine.core.parser.jaxb.BpmnEndEvent;
import uz.salvadore.processengine.core.parser.jaxb.BpmnError;
import uz.salvadore.processengine.core.parser.jaxb.BpmnExclusiveGateway;
import uz.salvadore.processengine.core.parser.jaxb.BpmnFlowElement;
import uz.salvadore.processengine.core.parser.jaxb.BpmnParallelGateway;
import uz.salvadore.processengine.core.parser.jaxb.BpmnServiceTask;
import uz.salvadore.processengine.core.parser.jaxb.BpmnStartEvent;

import java.time.Duration;
import java.util.List;
import java.util.Map;

public class FlowNodeMapper {

    private static final int DEFAULT_RETRY_COUNT = 3;
    private static final Duration DEFAULT_RETRY_INTERVAL = Duration.ofSeconds(10);

    public FlowNode map(BpmnFlowElement element, Map<String, BpmnError> errorMap) {
        return switch (element) {
            case BpmnStartEvent e -> mapStartEvent(e);
            case BpmnEndEvent e -> mapEndEvent(e, errorMap);
            case BpmnServiceTask e -> mapServiceTask(e);
            case BpmnExclusiveGateway e -> mapExclusiveGateway(e);
            case BpmnParallelGateway e -> mapParallelGateway(e);
            case BpmnCallActivity e -> mapCallActivity(e);
            case BpmnBoundaryEvent e -> mapBoundaryEvent(e, errorMap);
            default -> throw new BpmnParseException("Unknown BPMN element type: " + element.getClass().getSimpleName());
        };
    }

    private StartEvent mapStartEvent(BpmnStartEvent element) {
        return new StartEvent(
                element.getId(),
                element.getName(),
                List.copyOf(element.getIncoming()),
                List.copyOf(element.getOutgoing()));
    }

    private EndEvent mapEndEvent(BpmnEndEvent element, Map<String, BpmnError> errorMap) {
        String errorCode = null;
        if (element.isErrorEndEvent() && element.getErrorEventDefinition().getErrorRef() != null) {
            BpmnError bpmnError = errorMap.get(element.getErrorEventDefinition().getErrorRef());
            if (bpmnError != null) {
                errorCode = bpmnError.getErrorCode();
            }
        }
        return new EndEvent(
                element.getId(),
                element.getName(),
                List.copyOf(element.getIncoming()),
                List.copyOf(element.getOutgoing()),
                errorCode);
    }

    private ServiceTask mapServiceTask(BpmnServiceTask element) {
        return new ServiceTask(
                element.getId(),
                element.getName(),
                List.copyOf(element.getIncoming()),
                List.copyOf(element.getOutgoing()),
                element.getTopic(),
                DEFAULT_RETRY_COUNT,
                DEFAULT_RETRY_INTERVAL);
    }

    private ExclusiveGateway mapExclusiveGateway(BpmnExclusiveGateway element) {
        return new ExclusiveGateway(
                element.getId(),
                element.getName(),
                List.copyOf(element.getIncoming()),
                List.copyOf(element.getOutgoing()));
    }

    private ParallelGateway mapParallelGateway(BpmnParallelGateway element) {
        return new ParallelGateway(
                element.getId(),
                element.getName(),
                List.copyOf(element.getIncoming()),
                List.copyOf(element.getOutgoing()));
    }

    private CallActivity mapCallActivity(BpmnCallActivity element) {
        return new CallActivity(
                element.getId(),
                element.getName(),
                List.copyOf(element.getIncoming()),
                List.copyOf(element.getOutgoing()),
                element.getCalledElement());
    }

    private FlowNode mapBoundaryEvent(BpmnBoundaryEvent element, Map<String, BpmnError> errorMap) {
        if (element.isTimerBoundary()) {
            String durationStr = element.getTimerEventDefinition().getTimeDuration().getValue().trim();
            return new TimerBoundaryEvent(
                    element.getId(),
                    element.getName(),
                    List.copyOf(element.getIncoming()),
                    List.copyOf(element.getOutgoing()),
                    element.getAttachedToRef(),
                    Duration.parse(durationStr),
                    element.isCancelActivity());
        } else if (element.isErrorBoundary()) {
            String errorCode = null;
            if (element.getErrorEventDefinition().getErrorRef() != null) {
                BpmnError bpmnError = errorMap.get(element.getErrorEventDefinition().getErrorRef());
                if (bpmnError != null) {
                    errorCode = bpmnError.getErrorCode();
                }
            }
            return new ErrorBoundaryEvent(
                    element.getId(),
                    element.getName(),
                    List.copyOf(element.getIncoming()),
                    List.copyOf(element.getOutgoing()),
                    element.getAttachedToRef(),
                    errorCode,
                    element.isCancelActivity());
        } else if (element.isCompensationBoundary()) {
            return new CompensationBoundaryEvent(
                    element.getId(),
                    element.getName(),
                    List.copyOf(element.getIncoming()),
                    List.copyOf(element.getOutgoing()),
                    element.getAttachedToRef());
        }
        throw new BpmnParseException("Unknown boundary event type for element: " + element.getId());
    }
}
