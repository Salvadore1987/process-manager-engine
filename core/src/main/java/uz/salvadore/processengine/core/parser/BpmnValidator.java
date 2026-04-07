package uz.salvadore.processengine.core.parser;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class BpmnValidator {

    private static final String BPMN_NAMESPACE = "http://www.omg.org/spec/BPMN/20100524/MODEL";

    private static final Set<String> SUPPORTED_ELEMENTS = Set.of(
            "definitions",
            "process",
            "startEvent",
            "endEvent",
            "serviceTask",
            "exclusiveGateway",
            "parallelGateway",
            "callActivity",
            "boundaryEvent",
            "intermediateCatchEvent",
            "sequenceFlow",
            "association",
            "error",
            "incoming",
            "outgoing",
            "conditionExpression",
            "timerEventDefinition",
            "timeDuration",
            "timeDate",
            "timeCycle",
            "errorEventDefinition",
            "compensateEventDefinition"
    );

    private static final Set<String> FLOW_NODE_ELEMENTS = Set.of(
            "startEvent",
            "endEvent",
            "serviceTask",
            "exclusiveGateway",
            "parallelGateway",
            "callActivity",
            "boundaryEvent",
            "task",
            "userTask",
            "scriptTask",
            "businessRuleTask",
            "sendTask",
            "receiveTask",
            "manualTask",
            "subProcess",
            "transaction",
            "adHocSubProcess",
            "inclusiveGateway",
            "eventBasedGateway",
            "complexGateway",
            "intermediateThrowEvent"
    );

    public BpmnValidationResult validate(String bpmnXml) {
        List<UnsupportedElementError> errors = new ArrayList<>();
        XMLInputFactory factory = XMLInputFactory.newInstance();
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);

        try {
            XMLStreamReader reader = factory.createXMLStreamReader(new StringReader(bpmnXml));
            while (reader.hasNext()) {
                int event = reader.next();
                if (event == XMLStreamConstants.START_ELEMENT) {
                    String namespace = reader.getNamespaceURI();
                    String localName = reader.getLocalName();

                    if (BPMN_NAMESPACE.equals(namespace)
                            && !SUPPORTED_ELEMENTS.contains(localName)
                            && FLOW_NODE_ELEMENTS.contains(localName)) {
                        String id = reader.getAttributeValue(null, "id");
                        String name = reader.getAttributeValue(null, "name");
                        int line = reader.getLocation().getLineNumber();
                        errors.add(new UnsupportedElementError(
                                "bpmn:" + localName, id, name, line));
                    }
                }
            }
            reader.close();
        } catch (XMLStreamException e) {
            throw new BpmnParseException("Failed to parse BPMN XML for validation", e);
        }

        if (errors.isEmpty()) {
            return BpmnValidationResult.success();
        }
        return BpmnValidationResult.failure(errors);
    }
}
