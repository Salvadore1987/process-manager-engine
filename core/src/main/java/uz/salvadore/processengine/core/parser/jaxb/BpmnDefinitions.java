package uz.salvadore.processengine.core.parser.jaxb;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;

import java.util.ArrayList;
import java.util.List;

@XmlRootElement(name = "definitions", namespace = BpmnNamespaces.BPMN)
@XmlAccessorType(XmlAccessType.FIELD)
public class BpmnDefinitions {

    @XmlAttribute
    private String id;

    @XmlAttribute
    private String targetNamespace;

    @XmlElement(name = "process", namespace = BpmnNamespaces.BPMN)
    private List<BpmnProcess> processes = new ArrayList<>();

    @XmlElement(name = "error", namespace = BpmnNamespaces.BPMN)
    private List<BpmnError> errors = new ArrayList<>();

    public String getId() {
        return id;
    }

    public String getTargetNamespace() {
        return targetNamespace;
    }

    public List<BpmnProcess> getProcesses() {
        return processes;
    }

    public List<BpmnError> getErrors() {
        return errors;
    }
}
