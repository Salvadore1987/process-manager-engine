package uz.salvadore.processengine.core.parser.jaxb;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlValue;

@XmlAccessorType(XmlAccessType.FIELD)
public class BpmnTimeDuration {

    @XmlValue
    private String value;

    public String getValue() {
        return value;
    }
}
