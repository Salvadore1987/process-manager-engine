package uz.salvadore.processengine.core.parser.jaxb;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;

@XmlAccessorType(XmlAccessType.FIELD)
public class BpmnExclusiveGateway extends BpmnFlowElement {

    @XmlAttribute(name = "default")
    private String defaultFlow;

    public String getDefaultFlow() {
        return defaultFlow;
    }
}
