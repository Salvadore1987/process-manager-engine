package uz.salvadore.processengine.core.parser.jaxb;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;

@XmlAccessorType(XmlAccessType.FIELD)
public class BpmnErrorEventDefinition {

    @XmlAttribute
    private String id;

    @XmlAttribute
    private String errorRef;

    public String getId() {
        return id;
    }

    public String getErrorRef() {
        return errorRef;
    }
}
