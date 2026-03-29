package uz.salvadore.processengine.core.parser.jaxb;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElement;

@XmlAccessorType(XmlAccessType.FIELD)
public class BpmnBoundaryEvent extends BpmnFlowElement {

    @XmlAttribute
    private String attachedToRef;

    @XmlAttribute
    private Boolean cancelActivity;

    @XmlElement(name = "timerEventDefinition", namespace = BpmnNamespaces.BPMN)
    private BpmnTimerEventDefinition timerEventDefinition;

    @XmlElement(name = "errorEventDefinition", namespace = BpmnNamespaces.BPMN)
    private BpmnErrorEventDefinition errorEventDefinition;

    @XmlElement(name = "compensateEventDefinition", namespace = BpmnNamespaces.BPMN)
    private BpmnCompensateEventDefinition compensateEventDefinition;

    public String getAttachedToRef() {
        return attachedToRef;
    }

    public boolean isCancelActivity() {
        return cancelActivity == null || cancelActivity;
    }

    public BpmnTimerEventDefinition getTimerEventDefinition() {
        return timerEventDefinition;
    }

    public BpmnErrorEventDefinition getErrorEventDefinition() {
        return errorEventDefinition;
    }

    public BpmnCompensateEventDefinition getCompensateEventDefinition() {
        return compensateEventDefinition;
    }

    public boolean isTimerBoundary() {
        return timerEventDefinition != null;
    }

    public boolean isErrorBoundary() {
        return errorEventDefinition != null;
    }

    public boolean isCompensationBoundary() {
        return compensateEventDefinition != null;
    }
}
