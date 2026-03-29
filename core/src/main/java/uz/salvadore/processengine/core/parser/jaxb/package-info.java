@XmlSchema(
        namespace = BpmnNamespaces.BPMN,
        elementFormDefault = XmlNsForm.QUALIFIED,
        xmlns = {
                @XmlNs(prefix = "bpmn", namespaceURI = BpmnNamespaces.BPMN),
                @XmlNs(prefix = "camunda", namespaceURI = BpmnNamespaces.CAMUNDA)
        }
)
package uz.salvadore.processengine.core.parser.jaxb;

import jakarta.xml.bind.annotation.XmlNs;
import jakarta.xml.bind.annotation.XmlNsForm;
import jakarta.xml.bind.annotation.XmlSchema;
