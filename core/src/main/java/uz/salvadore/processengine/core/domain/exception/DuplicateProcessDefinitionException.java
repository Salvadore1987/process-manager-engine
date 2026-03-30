package uz.salvadore.processengine.core.domain.exception;

public class DuplicateProcessDefinitionException extends RuntimeException {

    public DuplicateProcessDefinitionException(String key, int version) {
        super("Process definition '" + key + "' version " + version
                + " has identical BPMN content to the latest deployed version");
    }
}
