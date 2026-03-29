package uz.salvadore.processengine.rest.exception;

import java.util.UUID;

public class ProcessNotFoundException extends RuntimeException {

    public ProcessNotFoundException(UUID processInstanceId) {
        super("Process instance not found: " + processInstanceId);
    }
}
