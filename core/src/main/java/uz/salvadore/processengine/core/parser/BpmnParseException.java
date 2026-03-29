package uz.salvadore.processengine.core.parser;

public class BpmnParseException extends RuntimeException {

    public BpmnParseException(String message) {
        super(message);
    }

    public BpmnParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
