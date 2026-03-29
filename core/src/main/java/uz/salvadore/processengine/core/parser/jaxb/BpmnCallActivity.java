package uz.salvadore.processengine.core.parser.jaxb;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;

@XmlAccessorType(XmlAccessType.FIELD)
public class BpmnCallActivity extends BpmnFlowElement {

    @XmlAttribute
    private String calledElement;

    public String getCalledElement() {
        return calledElement;
    }
}
