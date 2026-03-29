package uz.salvadore.processengine.core.parser.jaxb;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlElements;

import java.util.ArrayList;
import java.util.List;

@XmlAccessorType(XmlAccessType.FIELD)
public class BpmnProcess {

    @XmlAttribute
    private String id;

    @XmlAttribute
    private String name;

    @XmlAttribute
    private boolean isExecutable;

    @XmlElements({
            @XmlElement(name = "startEvent", namespace = BpmnNamespaces.BPMN, type = BpmnStartEvent.class),
            @XmlElement(name = "endEvent", namespace = BpmnNamespaces.BPMN, type = BpmnEndEvent.class),
            @XmlElement(name = "serviceTask", namespace = BpmnNamespaces.BPMN, type = BpmnServiceTask.class),
            @XmlElement(name = "exclusiveGateway", namespace = BpmnNamespaces.BPMN, type = BpmnExclusiveGateway.class),
            @XmlElement(name = "parallelGateway", namespace = BpmnNamespaces.BPMN, type = BpmnParallelGateway.class),
            @XmlElement(name = "callActivity", namespace = BpmnNamespaces.BPMN, type = BpmnCallActivity.class),
            @XmlElement(name = "boundaryEvent", namespace = BpmnNamespaces.BPMN, type = BpmnBoundaryEvent.class)
    })
    private List<BpmnFlowElement> flowElements = new ArrayList<>();

    @XmlElement(name = "sequenceFlow", namespace = BpmnNamespaces.BPMN)
    private List<BpmnSequenceFlow> sequenceFlows = new ArrayList<>();

    @XmlElement(name = "association", namespace = BpmnNamespaces.BPMN)
    private List<BpmnAssociation> associations = new ArrayList<>();

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public boolean isExecutable() {
        return isExecutable;
    }

    public List<BpmnFlowElement> getFlowElements() {
        return flowElements;
    }

    public List<BpmnSequenceFlow> getSequenceFlows() {
        return sequenceFlows;
    }

    public List<BpmnAssociation> getAssociations() {
        return associations;
    }
}
