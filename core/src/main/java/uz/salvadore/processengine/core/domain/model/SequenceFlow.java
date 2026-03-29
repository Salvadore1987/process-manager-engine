package uz.salvadore.processengine.core.domain.model;

public record SequenceFlow(
        String id,
        String sourceRef,
        String targetRef,
        ConditionExpression conditionExpression
) {
}
