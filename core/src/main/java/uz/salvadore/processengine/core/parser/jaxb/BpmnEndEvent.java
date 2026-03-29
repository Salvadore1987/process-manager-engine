package uz.salvadore.processengine.core.parser.jaxb;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;

@XmlAccessorType(XmlAccessType.FIELD)
public class BpmnEndEvent extends BpmnFlowElement {

    @XmlElement(name = "errorEventDefinition", namespace = BpmnNamespaces.BPMN)
    private BpmnErrorEventDefinition errorEventDefinition;

    public BpmnErrorEventDefinition getErrorEventDefinition() {
        return errorEventDefinition;
    }

    public boolean isErrorEndEvent() {
        return errorEventDefinition != null;
    }
}
