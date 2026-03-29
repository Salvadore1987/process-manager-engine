package uz.salvadore.processengine.core.parser;

import java.util.List;

public record BpmnValidationResult(
        boolean valid,
        List<UnsupportedElementError> unsupportedElements
) {

    public static BpmnValidationResult success() {
        return new BpmnValidationResult(true, List.of());
    }

    public static BpmnValidationResult failure(List<UnsupportedElementError> errors) {
        return new BpmnValidationResult(false, List.copyOf(errors));
    }
}
