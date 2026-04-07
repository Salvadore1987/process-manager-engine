package uz.salvadore.processengine.core.domain.exception;

public class CallActivitySubprocessNotFoundException extends RuntimeException {

    private final String calledElement;
    private final String expectedFileName;
    private final String parentProcessKey;

    public CallActivitySubprocessNotFoundException(String calledElement,
                                                    String expectedFileName,
                                                    String parentProcessKey) {
        super("Cannot deploy process '" + parentProcessKey
                + "': Call Activity references subprocess '" + calledElement
                + "' but file '" + expectedFileName
                + "' was not found in the deployment bundle");
        this.calledElement = calledElement;
        this.expectedFileName = expectedFileName;
        this.parentProcessKey = parentProcessKey;
    }

    public String getCalledElement() {
        return calledElement;
    }

    public String getExpectedFileName() {
        return expectedFileName;
    }

    public String getParentProcessKey() {
        return parentProcessKey;
    }
}
