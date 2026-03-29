package uz.salvadore.processengine.core.parser.mapper;

import uz.salvadore.processengine.core.domain.model.ConditionExpression;
import uz.salvadore.processengine.core.domain.model.SequenceFlow;
import uz.salvadore.processengine.core.parser.jaxb.BpmnSequenceFlow;

public class SequenceFlowMapper {

    public SequenceFlow map(BpmnSequenceFlow bpmnFlow) {
        ConditionExpression condition = null;
        if (bpmnFlow.getConditionExpression() != null
                && bpmnFlow.getConditionExpression().getExpression() != null) {
            condition = new ConditionExpression(
                    bpmnFlow.getConditionExpression().getExpression().trim());
        }
        return new SequenceFlow(
                bpmnFlow.getId(),
                bpmnFlow.getSourceRef(),
                bpmnFlow.getTargetRef(),
                condition);
    }
}
