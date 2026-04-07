package uz.salvadore.processengine.core.parser.jaxb;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;

@XmlAccessorType(XmlAccessType.FIELD)
public class BpmnIntermediateCatchEvent extends BpmnFlowElement {

    @XmlElement(name = "timerEventDefinition", namespace = BpmnNamespaces.BPMN)
    private BpmnTimerEventDefinition timerEventDefinition;

    public boolean isTimerEvent() {
        return timerEventDefinition != null;
    }

    public BpmnTimerEventDefinition getTimerEventDefinition() {
        return timerEventDefinition;
    }
}
