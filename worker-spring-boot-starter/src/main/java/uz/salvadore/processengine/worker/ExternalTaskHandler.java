package uz.salvadore.processengine.worker;

/**
 * Interface for handling external tasks dispatched by the process engine.
 * Implementations must be annotated with {@link uz.salvadore.processengine.worker.annotation.TaskHandler}
 * and registered as Spring beans.
 */
public interface ExternalTaskHandler {

    /**
     * Executes the task. Implementations must call either
     * {@link TaskContext#complete(java.util.Map)} or
     * {@link TaskContext#error(String, String)} to respond.
     *
     * @param context the task context containing variables and response methods
     */
    void execute(TaskContext context);
}
