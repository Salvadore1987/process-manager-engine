package uz.salvadore.processengine.core.parser;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Unmarshaller;
import uz.salvadore.processengine.core.domain.model.ProcessDefinition;
import uz.salvadore.processengine.core.parser.jaxb.BpmnDefinitions;
import uz.salvadore.processengine.core.parser.mapper.FlowNodeMapper;
import uz.salvadore.processengine.core.parser.mapper.ProcessDefinitionMapper;
import uz.salvadore.processengine.core.parser.mapper.SequenceFlowMapper;

import java.io.StringReader;
import java.util.List;

public class BpmnParser {

    private static final JAXBContext JAXB_CONTEXT;

    static {
        try {
            JAXB_CONTEXT = JAXBContext.newInstance(BpmnDefinitions.class);
        } catch (JAXBException e) {
            throw new ExceptionInInitializerError("Failed to initialize JAXB context: " + e.getMessage());
        }
    }

    private final BpmnValidator validator;
    private final ProcessDefinitionMapper definitionMapper;

    public BpmnParser() {
        this.validator = new BpmnValidator();
        this.definitionMapper = new ProcessDefinitionMapper(
                new FlowNodeMapper(), new SequenceFlowMapper());
    }

    public List<ProcessDefinition> parse(String bpmnXml) {
        BpmnValidationResult validationResult = validator.validate(bpmnXml);
        if (!validationResult.valid()) {
            throw new BpmnParseException(
                    "BPMN contains unsupported elements: " +
                            validationResult.unsupportedElements().stream()
                                    .map(UnsupportedElementError::element)
                                    .toList());
        }

        BpmnDefinitions definitions = unmarshal(bpmnXml);
        return definitionMapper.map(definitions, bpmnXml);
    }

    public BpmnValidationResult validate(String bpmnXml) {
        return validator.validate(bpmnXml);
    }

    public BpmnDefinitions unmarshal(String bpmnXml) {
        try {
            Unmarshaller unmarshaller = JAXB_CONTEXT.createUnmarshaller();
            return (BpmnDefinitions) unmarshaller.unmarshal(new StringReader(bpmnXml));
        } catch (JAXBException e) {
            throw new BpmnParseException("Failed to parse BPMN XML", e);
        }
    }
}
