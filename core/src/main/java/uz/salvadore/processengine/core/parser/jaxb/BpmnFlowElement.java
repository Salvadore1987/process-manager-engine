package uz.salvadore.processengine.core.parser.jaxb;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElement;

import java.util.ArrayList;
import java.util.List;

@XmlAccessorType(XmlAccessType.FIELD)
public abstract class BpmnFlowElement {

    @XmlAttribute
    private String id;

    @XmlAttribute
    private String name;

    @XmlElement(name = "incoming", namespace = BpmnNamespaces.BPMN)
    private List<String> incoming = new ArrayList<>();

    @XmlElement(name = "outgoing", namespace = BpmnNamespaces.BPMN)
    private List<String> outgoing = new ArrayList<>();

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public List<String> getIncoming() {
        return incoming;
    }

    public List<String> getOutgoing() {
        return outgoing;
    }
}
