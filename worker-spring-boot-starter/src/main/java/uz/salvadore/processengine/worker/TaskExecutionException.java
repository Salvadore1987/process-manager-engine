package uz.salvadore.processengine.worker;

/**
 * Thrown by a task handler to indicate a domain-level failure.
 * <p>
 * When this exception is caught by the listener, an error response is published
 * containing {@code __error=true}, {@code __errorCode}, and {@code message}.
 * </p>
 */
public class TaskExecutionException extends RuntimeException {

    private final String errorCode;

    public TaskExecutionException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
