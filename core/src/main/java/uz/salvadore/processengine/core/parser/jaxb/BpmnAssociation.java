package uz.salvadore.processengine.core.parser.jaxb;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;

@XmlAccessorType(XmlAccessType.FIELD)
public class BpmnAssociation {

    @XmlAttribute
    private String id;

    @XmlAttribute
    private String sourceRef;

    @XmlAttribute
    private String targetRef;

    @XmlAttribute
    private String associationDirection;

    public String getId() {
        return id;
    }

    public String getSourceRef() {
        return sourceRef;
    }

    public String getTargetRef() {
        return targetRef;
    }

    public String getAssociationDirection() {
        return associationDirection;
    }
}
