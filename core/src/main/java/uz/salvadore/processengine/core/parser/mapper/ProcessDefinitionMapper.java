package uz.salvadore.processengine.core.parser.mapper;

import uz.salvadore.processengine.core.domain.model.FlowNode;
import uz.salvadore.processengine.core.domain.model.ProcessDefinition;
import uz.salvadore.processengine.core.domain.model.SequenceFlow;
import uz.salvadore.processengine.core.parser.jaxb.BpmnDefinitions;
import uz.salvadore.processengine.core.parser.jaxb.BpmnError;
import uz.salvadore.processengine.core.parser.jaxb.BpmnProcess;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ProcessDefinitionMapper {

    private final FlowNodeMapper flowNodeMapper;
    private final SequenceFlowMapper sequenceFlowMapper;

    public ProcessDefinitionMapper(FlowNodeMapper flowNodeMapper, SequenceFlowMapper sequenceFlowMapper) {
        this.flowNodeMapper = flowNodeMapper;
        this.sequenceFlowMapper = sequenceFlowMapper;
    }

    public List<ProcessDefinition> map(BpmnDefinitions definitions, String bpmnXml) {
        Map<String, BpmnError> errorMap = definitions.getErrors().stream()
                .collect(Collectors.toMap(BpmnError::getId, error -> error));

        return definitions.getProcesses().stream()
                .filter(BpmnProcess::isExecutable)
                .map(process -> mapProcess(process, errorMap, bpmnXml))
                .toList();
    }

    private ProcessDefinition mapProcess(BpmnProcess process, Map<String, BpmnError> errorMap, String bpmnXml) {
        List<FlowNode> flowNodes = process.getFlowElements().stream()
                .map(element -> flowNodeMapper.map(element, errorMap))
                .toList();

        List<SequenceFlow> sequenceFlows = process.getSequenceFlows().stream()
                .map(sequenceFlowMapper::map)
                .toList();

        return ProcessDefinition.create(
                process.getId(),
                1,
                process.getName(),
                bpmnXml,
                flowNodes,
                sequenceFlows);
    }
}
