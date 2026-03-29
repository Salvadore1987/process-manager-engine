package uz.salvadore.processengine.rest.exception;

public class DefinitionNotFoundException extends RuntimeException {

    public DefinitionNotFoundException(String key) {
        super("Process definition not found: " + key);
    }
}
