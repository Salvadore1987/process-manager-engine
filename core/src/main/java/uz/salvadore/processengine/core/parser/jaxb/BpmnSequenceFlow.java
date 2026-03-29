package uz.salvadore.processengine.core.parser.jaxb;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElement;

@XmlAccessorType(XmlAccessType.FIELD)
public class BpmnSequenceFlow {

    @XmlAttribute
    private String id;

    @XmlAttribute
    private String name;

    @XmlAttribute
    private String sourceRef;

    @XmlAttribute
    private String targetRef;

    @XmlElement(name = "conditionExpression", namespace = BpmnNamespaces.BPMN)
    private BpmnConditionExpression conditionExpression;

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getSourceRef() {
        return sourceRef;
    }

    public String getTargetRef() {
        return targetRef;
    }

    public BpmnConditionExpression getConditionExpression() {
        return conditionExpression;
    }
}
