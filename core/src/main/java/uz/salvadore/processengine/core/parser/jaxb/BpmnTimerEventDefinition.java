package uz.salvadore.processengine.core.parser.jaxb;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElement;

@XmlAccessorType(XmlAccessType.FIELD)
public class BpmnTimerEventDefinition {

    @XmlAttribute
    private String id;

    @XmlElement(name = "timeDuration", namespace = BpmnNamespaces.BPMN)
    private BpmnTimeDuration timeDuration;

    @XmlElement(name = "timeDate", namespace = BpmnNamespaces.BPMN)
    private BpmnTimeDuration timeDate;

    @XmlElement(name = "timeCycle", namespace = BpmnNamespaces.BPMN)
    private BpmnTimeDuration timeCycle;

    public String getId() {
        return id;
    }

    public BpmnTimeDuration getTimeDuration() {
        return timeDuration;
    }

    public BpmnTimeDuration getTimeDate() {
        return timeDate;
    }

    public BpmnTimeDuration getTimeCycle() {
        return timeCycle;
    }
}
