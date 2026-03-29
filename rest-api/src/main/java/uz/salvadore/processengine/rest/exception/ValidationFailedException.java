package uz.salvadore.processengine.rest.exception;

import uz.salvadore.processengine.core.parser.BpmnValidationResult;

public class ValidationFailedException extends RuntimeException {

    private final BpmnValidationResult validationResult;

    public ValidationFailedException(BpmnValidationResult validationResult) {
        super("BPMN validation failed: " + validationResult.unsupportedElements().size() + " unsupported elements");
        this.validationResult = validationResult;
    }

    public BpmnValidationResult getValidationResult() {
        return validationResult;
    }
}
