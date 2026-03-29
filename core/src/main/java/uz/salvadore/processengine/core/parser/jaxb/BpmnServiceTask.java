package uz.salvadore.processengine.core.parser.jaxb;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;

@XmlAccessorType(XmlAccessType.FIELD)
public class BpmnServiceTask extends BpmnFlowElement {

    @XmlAttribute(namespace = BpmnNamespaces.CAMUNDA)
    private String type;

    @XmlAttribute(namespace = BpmnNamespaces.CAMUNDA)
    private String topic;

    @XmlAttribute
    private boolean isForCompensation;

    public String getType() {
        return type;
    }

    public String getTopic() {
        return topic;
    }

    public boolean isForCompensation() {
        return isForCompensation;
    }
}
